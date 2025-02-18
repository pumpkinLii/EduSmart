package com.tianji.promotion.service;

import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 兑换码 服务类
 * </p>
 *
 * @author lj
 * @since 2025-01-07
 */
public interface IExchangeCodeService extends IService<ExchangeCode> {

    void asyncGenerateExchangeCode(Coupon coupon);

    boolean updateExchangeMark(long serialNum, boolean b);
}
