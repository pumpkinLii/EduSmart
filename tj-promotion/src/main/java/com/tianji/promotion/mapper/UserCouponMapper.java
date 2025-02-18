package com.tianji.promotion.mapper;

import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 Mapper 接口
 * </p>
 *
 * @author lj
 * @since 2025-01-08
 */
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    @Select("SELECT  c.id,c.discount_type,c.`specific`,c.threshold_amount,c.discount_value,c.max_discount_amount,uc.id as user_coupon_id "+
            "FROM coupon c " +
            "INNER JOIN user_coupon uc ON c.id = uc.coupon_id " +
            "WHERE uc.user_id = #{userId} AND uc.`status` = 1;")
    List<Coupon> queryMyCoupon(Long user);
}
