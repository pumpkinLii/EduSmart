package com.tianji.learning.mq;

import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;

import com.tianji.common.utils.CollUtils;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;

@Component
@Slf4j
@RequiredArgsConstructor
public class LessonChangeListener {
    final ILearningLessonService lessonService;

//    public LessonChangeListener(ILearningLessonService lessonService) {
//        this.lessonService = lessonService;
//    }

    // 监听
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.lesson.pay.queue", durable = "true"), //队列名称
            exchange = @Exchange(value = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_PAY_KEY
    ))
    public void onMsg(OrderBasicDTO dto) {
        log.info("LessonChangeListener监听到了消息 用户{}，添加课程{}", dto.getUserId(), dto.getCourseIds());
        // 1.校验
        if (dto.getUserId() == null || dto.getOrderId() == null || CollUtils.isEmpty(dto.getCourseIds())) {
            log.error("接收到MQ消息有误，订单数据为空");
            return;
        }
        // 2. 调用service 保存课程到课表
        lessonService.addUserLearning(dto.getUserId(), dto.getCourseIds());
    }
}
