package com.tianji.learning.mq;


import com.tianji.common.constants.MqConstants;
import com.tianji.learning.domain.dto.LikedTimesDTO;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class LikedRecordListener {
    private final IInteractionReplyService replyService;
    /**
     * QA问答监听 消费者
     * @param dto
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "qa.liked.times.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LIKE_RECORD_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.QA_LIKED_TIMES_KEY
    ))
    /**
     * 这里消费的是单个dto
     *     public void listenReplyLikedTimesChange(LikedTimesDTO dto){
     *         log.debug("监听到回答或评论{}的点赞数变更:{}", dto.getBizId(), dto.getLikedTimes());
     *         InteractionReply r = new InteractionReply();
     *         r.setId(dto.getBizId());
     *         r.setLikedTimes(dto.getLikedTimes());
     *         replyService.updateById(r);
     *     }
     */
    // 消费多个dto
    public void listenReplyLikedTimesChange(List<LikedTimesDTO> likedTimesDTOs) {
        log.debug("监听到回答或评论的点赞数变更");

        List<InteractionReply> list = new ArrayList<>(likedTimesDTOs.size());
        for (LikedTimesDTO dto : likedTimesDTOs) {
            InteractionReply r = new InteractionReply();
            r.setId(dto.getBizId());
            r.setLikedTimes(dto.getLikedTimes());
            list.add(r);
        }
        replyService.updateBatchById(list);
    }


}
