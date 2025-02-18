package com.tianji.api.client.promotion.fallback;

import com.tianji.api.client.learning.LearningClient;
import com.tianji.api.client.promotion.PromotionClient;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.util.List;

@Slf4j
public class PromotionClientFallback implements FallbackFactory<PromotionClient> {

    @Override
    public PromotionClient create(Throwable cause) {
        log.error("优惠券计算服务异常", cause);
        return new PromotionClient() {
            @Override
            public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourses) {
                return null;
            }
        };
    }
}
