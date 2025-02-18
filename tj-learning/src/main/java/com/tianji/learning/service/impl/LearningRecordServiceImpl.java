package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSearchDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author lj
 * @since 2024-12-27
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService lessonService;
    private final CourseClient courseClient;
    private final LearningRecordDelayTaskHandler taskHandler;

    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        Long userId = UserContext.getUser();
        // 查其他表得到课程相关信息
        LearningLesson lesson = lessonService.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            throw new BizIllegalException("该课程未加入课表");
        }
        List<LearningRecord> recordList = this.lambdaQuery()
                .eq(LearningRecord::getLessonId, lesson.getId())
                .list();
        // 封装结果
        LearningLessonDTO dto = new LearningLessonDTO();
        dto.setId(lesson.getId());
        dto.setLatestSectionId(lesson.getLatestSectionId());
        List<LearningRecordDTO> dtoList = BeanUtils.copyList(recordList, LearningRecordDTO.class);
        dto.setRecords(dtoList);

        return dto;
    }

    @Override
    public void addLearningRecord(LearningRecordFormDTO dto) {
        // 1. 获取id
        Long userId = UserContext.getUser();
        // 2. 处理学习记录表
        boolean isFinished = false;
        if (dto.getSectionType().equals(SectionType.VIDEO)) {
            // 2.1 视频播放记录
            isFinished = handleVideoRecord(userId, dto);
        } else {
            // 2.1 考试记录
            isFinished = handleExamRecord(userId, dto);
        }
        // 3. 处理课程表
        if (!isFinished) { // 不是第一次学完不需要处理课表
            return;
        }
        handleLessonData(dto);
    }

    private void handleLessonData(LearningRecordFormDTO dto) {
        // 1. 查询课表
        LearningLesson lesson = lessonService.lambdaQuery().eq(LearningLesson::getId, dto.getLessonId()).one();
        if (lesson == null) {
            throw new BizIllegalException("课表不存在");
        }
        boolean allFinished = false;
        // 2. 是否第一次学完
        // 3. 课程服务得到小结总数
        CourseFullInfoDTO cinfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (cinfo == null) {
            throw new BizIllegalException("课程不存在");
        }
        // 4. 是否学完全部小节
        allFinished = lesson.getLearnedSections() + 1 >= cinfo.getSectionNum();
        lessonService.lambdaUpdate()
                .set(lesson.getStatus() == LessonStatus.NOT_BEGIN, LearningLesson::getStatus, LessonStatus.LEARNING)
                .set(allFinished, LearningLesson::getStatus, LessonStatus.FINISHED)
                .set(LearningLesson::getLatestLearnTime, dto.getCommitTime())
                .set(LearningLesson::getLatestSectionId, dto.getSectionId())
                .setSql("learned_sections = learned_sections + 1")
                .eq(LearningLesson::getId, lesson.getId())
                .update();
    }

    private boolean handleVideoRecord(Long userId, LearningRecordFormDTO dto) {
        // 查询某个小结的 学习记录
        LearningRecord learningRecord = queryOldRecord(dto.getLessonId(), dto.getSectionId());
        // 没学习记录 新增学习记录
        if (learningRecord == null) {
            // 不存在新增
            LearningRecord record = BeanUtils.copyBean(dto, LearningRecord.class);
            record.setUserId(userId);
            // 保存学习记录
            boolean result = this.save(record);
            if (!result) {
                throw new DbException("新增考试记录失败");
            }
            return false;
        }
        // 存在 更新 更新播放到第几秒，旧状态是未完成，本次播放超过50%
        boolean isFinished = !learningRecord.getFinished() && dto.getMoment() * 2 >= dto.getDuration();
        // 不是第一次学完
        if (!isFinished) {
            LearningRecord record  = new LearningRecord();
            record.setLessonId(dto.getLessonId());
            record.setSectionId(dto.getSectionId());
            record.setMoment(dto.getMoment());
            record.setFinished(learningRecord.getFinished());
            record.setId(learningRecord.getId());
            taskHandler.addLearningRecordTask(record);
            // 没学完
            return false;
        }
        boolean result = this.lambdaUpdate()
                .set(LearningRecord::getMoment, dto.getMoment())
                .set(isFinished, LearningRecord::getFinished, true)
                .set(isFinished, LearningRecord::getFinishTime, dto.getCommitTime())
                .eq(LearningRecord::getId, learningRecord.getId()).update();
        if (!result) {
            throw new DbException("更新视频学习记录失败");
        }
        // 清理redis缓存
        taskHandler.cleanRecordCache(dto.getLessonId(), dto.getSectionId());
        return true;
    }

    private LearningRecord queryOldRecord(Long lessonId, Long sectionId) {
        // 1. 查缓存
        LearningRecord cache = taskHandler.readRecordCache(lessonId, sectionId);
        // 2. 命中直接返回
        if (cache != null) {
            return cache;
        }
        // 3. 未命中查db
        LearningRecord dbRecord = this.lambdaQuery()
                .eq(LearningRecord::getLessonId, lessonId)
                .eq(LearningRecord::getSectionId, sectionId)
                .one();
        // 新增数据库的记录
        if (dbRecord == null) {
            return null;
        }
        // 4. 放入查db结果的缓存
        taskHandler.writeRecordCache(dbRecord);
        return dbRecord;
    }

    // 处理考试
    private boolean handleExamRecord(Long userId, LearningRecordFormDTO dto) {
        // dto 转 po
        LearningRecord record = BeanUtils.copyBean(dto, LearningRecord.class);
        record.setUserId(userId);
        record.setFinished(true);
        record.setFinishTime(dto.getCommitTime());
        // 保存学习记录
        boolean result = this.save(record);
        if (!result) {
            throw new DbException("新增考试记录失败");
        }
        return true;
    }
}
