package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动问题的回答或评论 前端控制器
 * </p>
 *
 * @author lj
 * @since 2024-12-29
 */
@RestController
@RequestMapping("/replies")
@Api(tags = "评论相关接口")
@Slf4j
@RequiredArgsConstructor
public class InteractionReplyController {
    final IInteractionReplyService interactionReplyService;

    @ApiOperation("新增回答或评论")
    @PostMapping
    public void saveReplies(@RequestBody @Validated ReplyDTO dto) {
        interactionReplyService.saveReplies(dto);
    }

    @ApiOperation("分页查询回答列表")
    @GetMapping("/page")
    public PageDTO<ReplyVO> queryReplies(ReplyPageQuery query) {
        PageDTO<ReplyVO> replyVOPageDTO = interactionReplyService.queryReplies(query);
        return replyVOPageDTO;
    }

}
