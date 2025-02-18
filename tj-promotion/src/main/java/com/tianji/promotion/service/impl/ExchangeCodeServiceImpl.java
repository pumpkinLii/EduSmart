package com.tianji.promotion.service.impl;

import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.mapper.ExchangeCodeMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.tianji.promotion.constants.PromotionConstants.COUPON_CODE_MAP_KEY;

/**
 * <p>
 * 兑换码 服务实现类
 * </p>
 *
 * @author lj
 * @since 2025-01-07
 */
@Service
@AllArgsConstructor
public class ExchangeCodeServiceImpl extends ServiceImpl<ExchangeCodeMapper, ExchangeCode> implements IExchangeCodeService {

    final StringRedisTemplate redisTemplate;

    @Async("generateExchangeCodeExecutor")
    @Override
    public void asyncGenerateExchangeCode(Coupon coupon) {
        Integer totalNum = coupon.getTotalNum();

        // 生成自增id 借助redis incrby 得到自增最大值
        Long increment = redisTemplate.opsForValue().increment(PromotionConstants.COUPON_CODE_SERIAL_KEY, totalNum);
        if (increment == null) {
            return;
        }
        int maxSerialNum = increment.intValue(); // 自增id最大值
        int begin = maxSerialNum - totalNum + 1;
        List<ExchangeCode> list = new ArrayList<>();
        for (int serialNum = begin; serialNum <= maxSerialNum; serialNum ++) {
            String code = CodeUtil.generateCode(serialNum, coupon.getId());// id,新鲜值(优惠券id，内部计算1-15之间数字找密钥）
            ExchangeCode exchangeCode = new ExchangeCode();
            exchangeCode.setId(serialNum);
            exchangeCode.setCode(code);
            exchangeCode.setExchangeTargetId(coupon.getId());
            exchangeCode.setExpiredTime(coupon.getIssueEndTime()); // 兑换码兑换截止时间
            list.add(exchangeCode);
        }
        this.saveBatch(list);


    }

    @Override
    public boolean updateExchangeMark(long serialNum, boolean mark) {
        Boolean boo = redisTemplate.opsForValue().setBit(COUPON_CODE_MAP_KEY, serialNum, mark);
        return boo != null && boo;
    }
}
