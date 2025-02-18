package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author lj
 * @since 2025-01-07
 */
@Service
@AllArgsConstructor
@Slf4j
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {
    final ICouponScopeService couponScopeService;
    final IExchangeCodeService exchangeCodeService;
    final IUserCouponService userCouponService;
    final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public void saveCoupon(CouponFormDTO dto) {
        Coupon coupon = BeanUtils.copyBean(dto, Coupon.class);
        this.save(coupon);
        if (!dto.getSpecific()) {
            return;
        }
        Long couponId = coupon.getId();
        List<Long> scopesList = dto.getScopes();
        if (CollUtils.isEmpty(scopesList)) {
            throw new BizIllegalException("限定的分类不能为空");
        }
        Set<CouponScope> scopes = scopesList.stream().map(id -> new CouponScope().setBizId(id).setCouponId(couponId).setType(1)).collect(Collectors.toSet());
        couponScopeService.saveBatch(scopes);

    }

    @Override
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query) {
        Page<Coupon> page = this.lambdaQuery()
                .eq(query.getType() != null, Coupon::getDiscountType, query.getType())
                .eq(query.getStatus() != null, Coupon::getStatus, query.getStatus())
                .like(query.getName() != null, Coupon::getName, query.getName())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<Coupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        List<CouponPageVO> voList = new ArrayList<>();
        for (Coupon record : records) {
            CouponPageVO vo = BeanUtils.copyBean(record, CouponPageVO.class);
            voList.add(vo);
        }
        return PageDTO.of(page, voList);
    }

    @Override
    public void beginIssue(CouponIssueFormDTO dto) {
        // 校验优惠券id 状态（待发放、暂停）
        if (dto.getId() == null) {
            throw new BizIllegalException("优惠券不存在");
        }
        Coupon coupon = this.getById(dto.getId());
        if (coupon.getStatus() != CouponStatus.DRAFT && coupon.getStatus() != CouponStatus.PAUSE) {
            throw new BizIllegalException("只有待发放和暂停的优惠券才能发放");
        }
        // 判断是否立即发放 不传开始时间或者开始时间早于当前时间
        LocalDateTime now = LocalDateTime.now();
        boolean isBeginIssue = dto.getIssueBeginTime() == null || !dto.getIssueBeginTime().isAfter(now); // 可以包含<=
        // 修改优惠券 领取开始和结束 使用开始和结束 状态 天数
        Coupon c = BeanUtils.copyBean(dto, Coupon.class);
        // 更新状态
        if (isBeginIssue) {
            c.setStatus(CouponStatus.ISSUING);
            c.setIssueBeginTime(now);
        }else{
            c.setStatus(CouponStatus.UN_ISSUE);
        }
        this.updateById(c);
        log.debug("isBeginIssue = {}", isBeginIssue);
        // 改造：如果优惠券立刻发放，存入redis hash
        if (isBeginIssue) {
            String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + dto.getId();
            Map<String, String> map = new HashMap<>();
            map.put("issueBeginTime", String.valueOf(DateUtils.toEpochMilli(now)));
            map.put("issueEndTime", String.valueOf(DateUtils.toEpochMilli(dto.getIssueEndTime())));
            map.put("totalNum", String.valueOf(coupon.getTotalNum()));
            map.put("userLimit", String.valueOf(coupon.getUserLimit()));
            redisTemplate.opsForHash().putAll(key, map);
            log.debug("优惠券缓存key = {}, value = {}", key, map);
        }


        // 优惠券的领取方式是指定发放 状态为未发放
        // TODO 兑换码生成
        if (coupon.getObtainWay() == ObtainType.ISSUE && coupon.getStatus() == CouponStatus.DRAFT) {
            coupon.setIssueEndTime(c.getIssueEndTime());
            exchangeCodeService.asyncGenerateExchangeCode(coupon);
            // 异步生成兑换码
        }
    }

    @Override
    public List<CouponVO> queryIssuingCoupons() {
        // 1.查询发放中的优惠券列表 发放中，手动领取
        List<Coupon> coupons = lambdaQuery()
                .eq(Coupon::getStatus, CouponStatus.ISSUING)
                .eq(Coupon::getObtainWay, ObtainType.PUBLIC)
                .list();
        if (CollUtils.isEmpty(coupons)) {
            return CollUtils.emptyList();
        }
        // 2.统计当前用户已经领取的优惠券的信息 发放中的
        List<Long> couponIds = coupons.stream().map(Coupon::getId).collect(Collectors.toList());
        // 2.1.查询当前用户已经领取的优惠券的数据
        List<UserCoupon> userCoupons = userCouponService.lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .in(UserCoupon::getCouponId, couponIds)
                .list();
        // 2.2.统计当前用户对优惠券的已经领取数量
        Map<Long, Long> issuedMap = userCoupons.stream()
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        // 2.3.统计当前用户对优惠券的已经领取并且未使用的数量
        Map<Long, Long> unusedMap = userCoupons.stream()
                .filter(uc -> uc.getStatus() == UserCouponStatus.UNUSED)
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        // 3.封装VO结果
        List<CouponVO> list = new ArrayList<>(coupons.size());
        for (Coupon c : coupons) {
            // 3.1.拷贝PO属性到VO
            CouponVO vo = BeanUtils.copyBean(c, CouponVO.class);
            list.add(vo);
            // 3.2.是否可以领取：1. 已经被领取的数量 < 优惠券总数量（没领完） && 当前用户已经领取的数量（统计） < 每人限领数量
            vo.setAvailable(
                    c.getIssueNum() < c.getTotalNum()
                            && issuedMap.getOrDefault(c.getId(), 0L) < c.getUserLimit()
            );
            // 3.3.是否可以使用：当前用户已经领取并且未使用的优惠券数量 > 0
            vo.setReceived(unusedMap.getOrDefault(c.getId(),  0L) > 0);
        }
        return list;
    }
}
