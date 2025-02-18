package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
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
@RequestMapping("/admin/questions")
@Api(tags = "问题相关接口-管理端")
@Slf4j
@RequiredArgsConstructor
public class InteractionQuestionAdminController {

    final IInteractionQuestionService interactionQuestionService;

    @ApiOperation("分页查询互动问题-管理端")
    @GetMapping("/page")
    public PageDTO<QuestionAdminVO> queryQuestionAdmin(QuestionAdminPageQuery query) {
        return interactionQuestionService.queryQuestionAdmin(query);
    }


}
