package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author lj
 * @since 2024-12-25
 */
@Api(tags = "我的课表相关接口")
@RestController
@RequestMapping("/lessons")
@Slf4j
@RequiredArgsConstructor
public class LearningLessonController {
    final ILearningLessonService lessonService;

    @ApiOperation("分页查询相关方法")
    @GetMapping("/page")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        return lessonService.queryMyLessons(query);
    }

    @ApiOperation("查询正在学习的课程")
    @GetMapping("/now")
    public LearningLessonVO queryMyCurrentLesson() {
        return lessonService.queryMyCurrentLesson();
    }

    @ApiOperation("查看课程是否有效")
    @GetMapping("/{courseId}/valid")
    public Long isLessonValid(@PathVariable("courseId") Long courseId) {
        return lessonService.isLessonValid(courseId);
    }

    @ApiOperation("查询课程状态")
    @GetMapping("/{courseId}")
    public LearningLessonVO getLessonInfo(@PathVariable("courseId") Long courseId) {
        return lessonService.getLessonInfo(courseId);
    }

    @ApiOperation("统计课程学习人数")
    @GetMapping("/{courseId}/count")
    public Integer countLearningLessonByCourse(@PathVariable("courseId") Long courseId) {
        return lessonService.countLearningLessonByCourse(courseId);
    }

    @ApiOperation("创建学习计划")
    @PostMapping("/plans")
    public void createLearningPlans(@RequestBody @Validated LearningPlanDTO dto) {
        lessonService.createLearningPlans(dto);
    }

    @ApiOperation("分页查询学习计划")
    @GetMapping("/plans")
    public LearningPlanPageVO queryMyPlans(PageQuery query) {
        return lessonService.queryMyPlans(query);
    }
}
