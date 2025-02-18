package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSearchDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.stereotype.Service;

import java.sql.Wrapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author lj
 * @since 2024-12-25
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {
    final CourseClient courseClient;
    final CatalogueClient catalogueClient;
    final LearningRecordMapper recordMapper;

    @Override
    public void addUserLearning(Long userId, List<Long> courseIds) {
        // Feign远程调用课程服务，得到课程信息
        List<CourseSimpleInfoDTO> cinfos = courseClient.getSimpleInfoList(courseIds);
        // 封装po实体类，填充过期时间
        if (CollUtils.isEmpty(cinfos)) {
            // 课程不存在，无法添加
            log.error("课程信息不存在，无法添加到课表");
            return;
        }
        List<LearningLesson> list = new ArrayList<>();
        for (CourseSimpleInfoDTO cinfo : cinfos) {
            LearningLesson lesson = new LearningLesson();
            lesson.setUserId(userId);
            lesson.setCourseId(cinfo.getId());
            Integer validDuration = cinfo.getValidDuration();
            if (validDuration != null && validDuration > 0) {
                LocalDateTime now = LocalDateTime.now();
                lesson.setCreateTime(now);
                lesson.setExpireTime(now.plusMonths(validDuration));
            }
            list.add(lesson);
        }
        // 批量保存
        this.saveBatch(list);
    }

    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        // 1. 获取登陆人
        Long userId = UserContext.getUser();

        // 2. 分页查询我的课表
        Page<LearningLesson> page = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 3. 查到的数据封装到返回给前端的数据当中
        Set<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> cinfos = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cinfos)) {
            throw new BizIllegalException("课程不存在");
        }
        // cinfos转换为map<课程id, 课程对象>
        Map<Long, CourseSimpleInfoDTO> infoDTOMap = cinfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        // 4. 远程调用课程服务给vo中需要的数据赋值
        List<LearningLessonVO> voList = new ArrayList<>();
        for (LearningLesson record: records) {
            LearningLessonVO vo = BeanUtils.copyBean(record, LearningLessonVO.class);
            CourseSimpleInfoDTO infoDTO = infoDTOMap.get(record.getCourseId());
            if (infoDTO != null) {
                vo.setCourseName(infoDTO.getName());
                vo.setCourseCoverUrl((infoDTO.getCoverUrl()));
                vo.setSections(infoDTO.getSectionNum());
            }
            voList.add(vo);
        }
        return PageDTO.of(page, voList);
    }

    @Override
    public LearningLessonVO queryMyCurrentLesson() {
        Long userId = UserContext.getUser();
        // 查一条 正在学习的 刚学的 降序查一条
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING)
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        // 远程调用获取其他信息
        CourseFullInfoDTO cinfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (cinfo == null) {
            throw new BizIllegalException("课程不存在");
        }
        // 查询总课程数
        Integer count = this.lambdaQuery().eq(LearningLesson::getUserId, userId).count();
        // 小结id 获取小结名称
        Long latestSectionId = lesson.getLatestSectionId();
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(CollUtils.singletonList(latestSectionId));
        if (CollUtils.isEmpty(cataSimpleInfoDTOS)) {
            throw new BizIllegalException("小结不存在");
        }
        // 封装到vo返回
        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        vo.setCourseName(cinfo.getName());
        vo.setCourseCoverUrl((cinfo.getCoverUrl()));
        vo.setSections(cinfo.getSectionNum());
        vo.setCourseAmount(count);
        vo.setLatestSectionName(cataSimpleInfoDTOS.get(0).getName());
        vo.setLatestSectionIndex(cataSimpleInfoDTOS.get(0).getCIndex());
        return vo;
    }

    @Override
    public Long isLessonValid(Long courseId) {
        // 1. 用户课表中是否有该课程
        Long userId = UserContext.getUser();
        LearningLesson lesson= this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            throw new BizIllegalException("用户不存在该课程");
        }
        // 2. 课程状态是否是有效的状态（未过期）
        LocalDateTime expireTime = lesson.getExpireTime();
        LocalDateTime now = LocalDateTime.now();
        if (expireTime != null && now.isAfter(expireTime)) {
            throw new BizIllegalException("课程已过期");
        }
        return lesson.getId();
    }

    @Override
    public LearningLessonVO getLessonInfo(Long courseId) {
        Long userId = UserContext.getUser();
        LearningLesson learningLesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId).one();
        LearningLessonVO learningLessonVO = BeanUtils.copyBean(learningLesson, LearningLessonVO.class);
//        LearningLessonVO learningLessonVO = LearningLessonVO.builder().id(learningLesson.getId())
//                .courseId(learningLesson.getCourseId())
//                .status(learningLesson.getStatus())
//                .learnedSections(learningLesson.getLearnedSections())
//                .createTime(learningLesson.getCreateTime())
//                .expireTime(learningLesson.getExpireTime())
//                .planStatus(learningLesson.getPlanStatus())
//                .build();
        return learningLessonVO;
    }

    @Override
    public Integer countLearningLessonByCourse(Long courseId) {
        return this.lambdaQuery().eq(LearningLesson::getCourseId, courseId).count();
    }

    @Override
    public void createLearningPlans(LearningPlanDTO dto) {
        Long userId = UserContext.getUser();
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, dto.getCourseId())
                .one();
        if (lesson == null) {
            throw new DbException("用户没有学习该课程");
        }
        boolean update = this.lambdaUpdate()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, dto.getCourseId())
                .set(lesson.getPlanStatus() == PlanStatus.NO_PLAN, LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .set(LearningLesson::getWeekFreq, dto.getFreq())
                .update();
        if (!update) {
            throw new DbException("学习计划修改失败");
        }
    }

    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery query) {
        Long userId = UserContext.getUser();
        // 本周学习计划的所有课程
        List<LearningLesson> lessons = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .list();
        if (CollUtils.isEmpty(lessons)) {
            throw new BizIllegalException("没有符合的学习计划");
        }
        int weekTotalPlan = lessons.stream().mapToInt(LearningLesson::getWeekFreq).sum();
        // 统计当前用户每个课程的已学习小节数量 时间在本周
        LocalDate now = LocalDate.now();
        LocalDateTime weekBeginTime = DateUtils.getWeekBeginTime(now);
        LocalDateTime weekEndTime = DateUtils.getWeekEndTime(now);
        List<LearningRecord> learningRecords = recordMapper.selectList(Wrappers.<LearningRecord>lambdaQuery()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .between(LearningRecord::getFinishTime, weekBeginTime, weekEndTime));
        Map<Long, Long> countMap = learningRecords.stream()
                .collect(Collectors.groupingBy(LearningRecord::getLessonId, Collectors.counting()));

        // 读lesson 数据
        Page<LearningLesson> page = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            LearningPlanPageVO vo = new LearningPlanPageVO();
            vo.setPages(0L);
            vo.setTotal(0L);
            vo.setList(new ArrayList<>());
            return vo;
        }
        List<LearningPlanVO> volist = new ArrayList<>();
        for (LearningLesson record: records) {
            LearningPlanVO planVO = BeanUtils.copyBean(record, LearningPlanVO.class);
            CourseFullInfoDTO cinfo = courseClient.getCourseInfoById(record.getCourseId(), false, false);
            planVO.setCourseName(cinfo.getName());
            planVO.setSections(cinfo.getSectionNum());

            planVO.setWeekLearnedSections(countMap.getOrDefault(record.getId(), 0L).intValue());
            volist.add(planVO);
        }
        LearningPlanPageVO vo = new LearningPlanPageVO();
        vo.setWeekTotalPlan(weekTotalPlan);
        vo.setWeekFinished(learningRecords.size());
        vo.setList(volist);
        vo.setTotal(page.getTotal());
        vo.setPages(page.getPages());
        return vo;
    }


}
