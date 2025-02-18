package com.tianji.promotion.handler;

import com.tianji.common.constants.MqConstants;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.service.IUserCouponService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;


@Component
@Slf4j
@AllArgsConstructor
public class PromotionCouponHandler {
    private final IUserCouponService userCouponService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "coupon.receive.queue", durable = "true"),
            exchange = @Exchange(name = PromotionConstants.Exchange.PROMOTION_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = PromotionConstants.Key.COUPON_RECEIVE
    ))
    public void listenCouponReceiveMessage(UserCouponDTO msg){
        log.debug("受到领券消息");
        userCouponService.checkAndCreateUserCouponNew(msg);
    }
}
