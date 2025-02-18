package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author lj
 * @since 2024-12-29
 */
@RestController
@RequestMapping("/questions")
@Api(tags = "问题相关接口-用户端")
@Slf4j
@RequiredArgsConstructor
public class InteractionQuestionController {

    final IInteractionQuestionService interactionQuestionService;

    @ApiOperation("新增互动问题")
    @PostMapping
    public void saveQuestion(@RequestBody @Validated QuestionFormDTO dto) {
        interactionQuestionService.saveQuestion(dto);
    }

    @ApiOperation("修改互动问题")
    @PutMapping("/{id}")
    public void updateQuestion(@PathVariable Long id, @RequestBody QuestionFormDTO dto) {
        interactionQuestionService.updateQuestion(id, dto);
    }

    @ApiOperation("分页查询互动问题-用户端")
    @GetMapping("/page")
    public PageDTO<QuestionVO> queryQuestion(QuestionPageQuery query) {
        return interactionQuestionService.queryQuestion(query);
    }

    @ApiOperation("根据id查询问题详情")
    @GetMapping("/{id}")
    public QuestionVO queryQuestionById(@PathVariable Long id) {
        return interactionQuestionService.queryQuestionById(id);
    }

}
