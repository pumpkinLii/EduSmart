package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;

import java.util.List;

/**
 * <p>
 * 学生课程表 服务类
 * </p>
 *
 * @author lj
 * @since 2024-12-25
 */
public interface ILearningLessonService extends IService<LearningLesson> {

    void addUserLearning(Long userId, List<Long> courseIds);

    PageDTO<LearningLessonVO> queryMyLessons(PageQuery query);

    LearningLessonVO queryMyCurrentLesson();

    Long isLessonValid(Long courseId);

    LearningLessonVO getLessonInfo(Long courseId);

    Integer countLearningLessonByCourse(Long courseId);

    void createLearningPlans(LearningPlanDTO dto);

    LearningPlanPageVO queryMyPlans(PageQuery query);
}
