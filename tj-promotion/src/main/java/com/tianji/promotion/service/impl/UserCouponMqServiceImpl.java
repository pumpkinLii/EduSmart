package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.discount.Discount;
import com.tianji.promotion.discount.DiscountStrategy;
import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.tianji.promotion.utils.CodeUtil;
import com.tianji.promotion.utils.MyLock;
import com.tianji.promotion.utils.MyLockType;
import com.tianji.promotion.utils.PermuteUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author lj
 * @since 2025-01-08
 */
@Service
@AllArgsConstructor
@Slf4j
//@Transactional // 不知道这里要加不  不加！！！ 为什么阿
public class UserCouponMqServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    final CouponMapper couponMapper;
    final IExchangeCodeService exchangeCodeService;
    final StringRedisTemplate redisTemplate;
    final RedissonClient redissonClient;
    final RabbitMqHelper mqHelper;
    final ICouponScopeService couponScopeService;
    final Executor calculateSolutionExecutor;

    @Override
//    @Transactional
    // 分布式锁对优惠券加锁
    @MyLock(name = "lock:coupon:uid:#{couponId}", lockType = MyLockType.RE_ENTRANT_LOCK)
    public void receiveCoupon(Long couponId) {
        Long userId = UserContext.getUser();
        // 查缓存
        Coupon coupon = queryCouponByCache(couponId);
        if (coupon == null) {
            throw new BizIllegalException("优惠券不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        boolean time = now.isAfter(coupon.getIssueBeginTime()) && now.isBefore(coupon.getIssueEndTime());
        if (!time) {
            throw new BizIllegalException("不在优惠券发放期间");
        }

        if (coupon.getTotalNum() <= 0) {
            throw new BizIllegalException("无库存");
        }

        // 统计已领取的数量
        String key = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + couponId;
        // 领取后的已领数量
        Long increment = redisTemplate.opsForHash().increment(key, userId.toString(), 1);
        // 校验是否超过限领数量
        if (increment > coupon.getUserLimit()) {
            throw new BizIllegalException("超出用户限领数量");
        }
        // 修改优惠券的库存
        String couponKey = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        redisTemplate.opsForHash().increment(couponKey, "totalNum", -1);
        // 发消息
        UserCouponDTO msg = new UserCouponDTO();
        msg.setCouponId(couponId);
        msg.setUserId(userId);
        mqHelper.send(PromotionConstants.Exchange.PROMOTION_EXCHANGE, PromotionConstants.Key.COUPON_RECEIVE, msg);

    }

    private Coupon queryCouponByCache(Long couponId) {
        String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        Coupon coupon = BeanUtils.mapToBean(entries, Coupon.class, false, CopyOptions.create());
        return coupon;
    }

    @Override
    @Transactional
    @MyLock(name = "lock:coupon:uid:#{userId}", lockType = MyLockType.RE_ENTRANT_LOCK)
    public void checkAndCreateUserCoupon(Coupon coupon, Long userId, Long serialNum) {
        //        synchronized (userId.toString().intern()) {
        // 1.校验每人限领数量
        // 1.1.统计当前用户对当前优惠券的已经领取的数量
        Integer count = lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, coupon.getId())
                .count();
        // 1.2.校验限领数量
        if(count != null && count >= coupon.getUserLimit()){
            throw new BadRequestException("超出领取数量");
        }
        // 2.更新优惠券的已经发放的数量 + 1
        couponMapper.incrIssueNum(coupon.getId());
        // 3.新增一个用户券
        saveUserCoupon(coupon, userId);
        // 4.更新兑换码状态
        if (serialNum != null) {
            exchangeCodeService.lambdaUpdate()
                    .set(ExchangeCode::getUserId, userId)
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .eq(ExchangeCode::getId, serialNum)
                    .update();
        }
//        throw new RuntimeException("故意错误");
//        }
    }

    private void saveUserCoupon(Coupon coupon, Long userId) {
        // 1.基本信息
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(coupon.getId());
        // 2.有效期信息
        LocalDateTime termBeginTime = coupon.getTermBeginTime();
        LocalDateTime termEndTime = coupon.getTermEndTime();
        if (termBeginTime == null) {
            termBeginTime = LocalDateTime.now();
            termEndTime = termBeginTime.plusDays(coupon.getTermDays());
        }
        uc.setTermBeginTime(termBeginTime);
        uc.setTermEndTime(termEndTime);
        // 3.保存
        save(uc);
    }

    @Override
    @Transactional
    public void exchangeCoupon(String code) {
        // 1.校验并解析兑换码
        long serialNum = CodeUtil.parseCode(code);
        log.debug("自增id{}", serialNum);
        // 2.校验是否已经兑换 SETBIT KEY 4 1 ，这里直接执行setbit，通过返回值来判断是否兑换过
        boolean exchanged = exchangeCodeService.updateExchangeMark(serialNum, true);
        if (exchanged) {
            throw new BizIllegalException("兑换码已经被兑换过了");
        }
        try {
            // 3.查询兑换码对应的优惠券id
            ExchangeCode exchangeCode = exchangeCodeService.getById(serialNum);
            if (exchangeCode == null) {
                throw new BizIllegalException("兑换码不存在！");
            }
            // 4.是否过期
            LocalDateTime now = LocalDateTime.now();
            if (now.isAfter(exchangeCode.getExpiredTime())) {
                throw new BizIllegalException("兑换码已经过期");
            }
            // 5.校验并生成用户券
            // 5.1.查询优惠券
            Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());
            // 5.2.查询用户
            Long userId = UserContext.getUser();
            // 5.3.校验并生成用户券，更新兑换码状态
            checkAndCreateUserCoupon(coupon, userId, serialNum);
        } catch (Exception e) {
            // 重置兑换的标记 0
            exchangeCodeService.updateExchangeMark(serialNum, false);
            throw e;
        }

    }

    @Override
    @Transactional // 记得加回去
    public void checkAndCreateUserCouponNew(UserCouponDTO msg) {
        Coupon coupon = couponMapper.selectById(msg.getCouponId());
        if (coupon == null) {
            return;
        }
        // 2.更新优惠券的已经发放的数量 + 1
        int num = couponMapper.incrIssueNum(coupon.getId());
        if (num == 0) {
            return;
        }
        // 3.新增一个用户券
        saveUserCoupon(coupon, msg.getUserId());
    }

    @Override
    public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourses) {
        // 查询当前用户可用的优惠券

        List<Coupon> coupons = getBaseMapper().queryMyCoupon(UserContext.getUser());
        if (CollUtils.isEmpty(coupons)) {
            return CollUtils.emptyList();
        }
        log.debug("优惠券{}张", coupons.size());
        for (Coupon coupon : coupons) {
            log.debug("筛选后：{},{}",
                    DiscountStrategy.getDiscount(coupon.getDiscountType()).getRule(coupon),
                    coupon
            );
        }
        // 2.初筛
        // 2.1.计算订单总价
        int totalAmount = orderCourses.stream().mapToInt(OrderCourseDTO::getPrice).sum();
        log.debug("金额{}", totalAmount);
        // 2.2.筛选可用券
//        List<Coupon> availableCoupons = new ArrayList<>();
//        for (Coupon coupon : coupons) {
//            boolean flag = DiscountStrategy.getDiscount(coupon.getDiscountType()).canUse(totalAmount, coupon));
//            if (flag) {
//                availableCoupons.add(coupon);
//            }
//        }
        List<Coupon> availableCoupons = coupons.stream()
                .filter(coupon -> DiscountStrategy.getDiscount(coupon.getDiscountType()).canUse(totalAmount, coupon))
                .collect(Collectors.toList());
        if (CollUtils.isEmpty(availableCoupons)) {
            return CollUtils.emptyList();
        }
        for (Coupon coupon : availableCoupons) {
            log.debug("筛选后：{},{}",
                    DiscountStrategy.getDiscount(coupon.getDiscountType()).getRule(coupon),
                    coupon
            );
        }
        log.debug("初筛后{}张", availableCoupons.size());
        // 细筛
        Map<Coupon, List<OrderCourseDTO>> availableCouponMap = findAvailableCoupon(availableCoupons, orderCourses);
        if (CollUtils.isEmpty(availableCouponMap)) {
            log.debug("CollUtils.isEmpty");
            return CollUtils.emptyList();
        }
//        Set<Map.Entry<Coupon, List<OrderCourseDTO>>> entries = availableCouponMap.entrySet();
//        for (Map.Entry<Coupon, List<OrderCourseDTO>> entry : entries) {
//            log.debug("细筛选后：{},{}",
//                    DiscountStrategy.getDiscount(entry.getKey().getDiscountType()).getRule(entry.getKey()),
//                    entry.getKey()
//            );
//        }
        Set<Coupon> couponsSet = availableCouponMap.keySet();
        // 真正可用的优惠券集合
        availableCoupons = new ArrayList<>(couponsSet);
        log.debug("细筛过的优惠券{}个", availableCoupons.size());
        for (Coupon coupon : availableCoupons) {
            log.debug("优惠券：{},{}",
                    DiscountStrategy.getDiscount(coupon.getDiscountType()).getRule(coupon),
                    coupon
            );
        }
        // 排列组合
        List<List<Coupon>> solutions = PermuteUtil.permute(availableCoupons);
        // 添加单券
        for (Coupon availableCoupon : availableCoupons) {
            solutions.add(List.of(availableCoupon));
        }
        log.debug("排列组合");
        for (List<Coupon> solution : solutions) {
            List<Long> ids = solution.stream().map(Coupon::getId).collect(Collectors.toList());
            log.debug("{}", ids);
        }
        // 4.计算方案的优惠明细
//        List<CouponDiscountDTO> dtos =
//                Collections.synchronizedList(new ArrayList<>(solutions.size()));
//        for (List<Coupon> solution : solutions) {
//            CouponDiscountDTO dto = calculateSolutionDiscount(availableCouponMap, orderCourses, solution);
//            log.debug("方案");
//            log.debug("方案最终优惠{},优惠券使用了{},规则{}", dto.getDiscountAmount(), dto.getIds(), dto.getRules());
//            dtos.add(dto);
//        }
//        // 5.筛选最优解
//        log.debug("list{}", dtos);
        // 线程安全的集合
        List<CouponDiscountDTO> dtos =
                Collections.synchronizedList(new ArrayList<>(solutions.size()));

        CountDownLatch latch = new CountDownLatch(solutions.size());
        for (List<Coupon> solution : solutions) {
            CompletableFuture.supplyAsync(new Supplier<CouponDiscountDTO>() {
                @Override
                public CouponDiscountDTO get() {
                    CouponDiscountDTO dto = calculateSolutionDiscount(availableCouponMap, orderCourses, solution);
                    return dto;
                }
            }, calculateSolutionExecutor).thenAccept(new Consumer<CouponDiscountDTO>() {
                @Override
                public void accept(CouponDiscountDTO dto) {
                    log.debug("方案最终优惠{},优惠券使用了{},规则{}", dto.getDiscountAmount(), dto.getIds(), dto.getRules());
                    dtos.add(dto);
                    latch.countDown();
                }
            });
        }
        try {
            latch.await(2, TimeUnit.SECONDS); // 主线程最多阻塞两秒
        } catch (InterruptedException e) {
            log.error("多线程处理优惠明细报错");
        }
        // 筛选最优解

        return findBestSolution(dtos);
    }

    private List<CouponDiscountDTO> findBestSolution(List<CouponDiscountDTO> solutions) {
        Map<String, CouponDiscountDTO> moreDiscountMap = new HashMap<>();
        Map<Integer, CouponDiscountDTO> lessDiscountMap = new HashMap<>();
        for (CouponDiscountDTO solution : solutions) {
            // 对优惠券id升序转字符串以逗号拼接
            String ids = solution.getIds().stream().sorted(Comparator.comparing(Long::longValue)).map(String::valueOf).collect(Collectors.joining(","));
            // 处理map1
            CouponDiscountDTO old = moreDiscountMap.get(ids);
            if (old != null && old.getDiscountAmount() >= solution.getDiscountAmount()) {
                continue;
            }
            // 当前方案更优
            moreDiscountMap.put(ids, solution);
            // 处理map2

            old = lessDiscountMap.get(solution.getDiscountAmount());
            int newSize = solution.getIds().size();
            if (old != null && newSize > 1 && old.getIds().size() <= newSize) {
                continue;
            }
            // 当前方案更优
            lessDiscountMap.put(solution.getDiscountAmount(), solution);
        }
        // 求交集
        Collection<CouponDiscountDTO> bestSolution = CollUtils.intersection(moreDiscountMap.values(), lessDiscountMap.values());
        List<CouponDiscountDTO> latestSolutions = bestSolution.stream().sorted(Comparator.comparing(CouponDiscountDTO::getDiscountAmount).reversed()).collect(Collectors.toList());
        return latestSolutions;
    }


    /**
     * 计算每一个方案的优惠明细
     * @param availableCouponMap 优惠券和可用的课程
     * @param orderCourses 所有课程
     * @param solution 方案
     * @return
     */
    private CouponDiscountDTO calculateSolutionDiscount(Map<Coupon, List<OrderCourseDTO>> availableCouponMap,
                                                        List<OrderCourseDTO> orderCourses,
                                                        List<Coupon> solution) {

        // 1. 创建方案结果dto
        CouponDiscountDTO dto = new CouponDiscountDTO();
        // 2. 映射 商品id -- 折扣明细 初始化0
        Map<Long, Integer> detailMap = orderCourses.stream().collect(Collectors.toMap(OrderCourseDTO::getId, OrderCourseDTO -> 0));

        // 3. 计算折扣方案
        // 3.1 循环方案中优惠券
        for (Coupon coupon : solution) {
            // 3.2 取出优惠券对应的可用课程
            List<OrderCourseDTO> orderCourseDTOS = availableCouponMap.get(coupon);
//            log.debug("detailMap: {}", detailMap);
//            log.debug("orderCourseDTOS: {}", orderCourseDTOS);

            // 3.3 计算可用课程金额
            int totalAmount = orderCourseDTOS.stream().mapToInt(new ToIntFunction<OrderCourseDTO>() {
                @Override
                public int applyAsInt(OrderCourseDTO value) {
                    // 商品价格 - 折扣明细
                    if (value.getId() == null) {
                        throw new IllegalStateException("订单课程 ID 为空：" + value);
                    }

                    return value.getPrice() - detailMap.get(value.getId());
                }
            }).sum();
            // 3.4 判断优惠券是否可用
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (!discount.canUse(totalAmount, coupon)) {
                continue;
            }
            // 3.5 计算优惠券使用后的折扣值
            int discountAmount = discount.calculateDiscount(totalAmount, coupon);
            // 3.6 更新商品折扣明细
            calculateDetailDiscount(detailMap, orderCourseDTOS, totalAmount, discountAmount);
            dto.getIds().add(coupon.getId());
            dto.getRules().add(discount.getRule(coupon));
            dto.setDiscountAmount(discountAmount + dto.getDiscountAmount()); // 累加所有生效的优惠券
        }
        // 3.7 累加每一个优惠券的优惠金额 赋值给dto
        return dto;
    }

    /**
     * 商品的折扣明细
     * @param detailMap id -- 优惠明细
     * @param orderCourseDTOS 课程集合
     * @param totalAmount 课程总金额（减去之前优惠）
     * @param discountAmount 可以优惠的金额
     */
    private void calculateDetailDiscount(Map<Long, Integer> detailMap, List<OrderCourseDTO> orderCourseDTOS, int totalAmount, int discountAmount) {
        int times = 0;
        int remainDiscount = discountAmount;
        for (OrderCourseDTO orderCourseDTO : orderCourseDTOS) {
            times ++;
            int discount = 0;
            if (times == orderCourseDTOS.size()) { // 最后一个
                discount = remainDiscount;
            } else {
                // 先乘再除！！！
//                discount = orderCourseDTO.getPrice() * discountAmount / totalAmount;
                discount = Math.round((float) orderCourseDTO.getPrice() * discountAmount / totalAmount);
                remainDiscount = remainDiscount - discount;
            }
//            detailMap.put(orderCourseDTO.getId(), detailMap.get(orderCourseDTO.getId() + discount));
            detailMap.put(orderCourseDTO.getId(), detailMap.getOrDefault(orderCourseDTO.getId(), 0) + discount);
            log.debug("更新后 detailMap: {}", detailMap);


            int remainCourses = orderCourseDTOS.size();

            // 按价格排序，价格相同的放在一起处理
            Map<Integer, List<OrderCourseDTO>> priceGroup = orderCourseDTOS.stream()
                    .collect(Collectors.groupingBy(OrderCourseDTO::getPrice));

            // 处理每个价格组
            for (Map.Entry<Integer, List<OrderCourseDTO>> entry : priceGroup.entrySet()) {
                List<OrderCourseDTO> samePriceCourses = entry.getValue();
                int coursePrice = entry.getKey();

                // 计算这个价格组应得的总优惠
                int groupDiscount = Math.round((float) coursePrice * samePriceCourses.size() * discountAmount / totalAmount);

                // 每个课程平均分配优惠
                int perCourseDiscount = groupDiscount / samePriceCourses.size();

                // 处理可能的余数
                int remainder = groupDiscount % samePriceCourses.size();

                // 给每个课程分配优惠
                for (int i = 0; i < samePriceCourses.size(); i++) {
                    OrderCourseDTO course = samePriceCourses.get(i);
                    int finalDiscount = perCourseDiscount;
                    if (i < remainder) {
                        finalDiscount += 1; // 将余数分配给前几个课程
                    }

                    detailMap.put(course.getId(), detailMap.getOrDefault(course.getId(), 0) + finalDiscount);
                    remainDiscount -= finalDiscount;
                    remainCourses--;
                }
            }

            log.debug("优惠分配结果: {}", detailMap);

        }
    }

    // 查每一个优惠券的可用课程
    private Map<Coupon, List<OrderCourseDTO>> findAvailableCoupon(List<Coupon> coupons, List<OrderCourseDTO> orderCourses) {
        Map<Coupon, List<OrderCourseDTO>> map = new HashMap<>(coupons.size());
        for (Coupon coupon : coupons) {
            // 1.找出优惠券的可用的
            List<OrderCourseDTO> availableCourses = orderCourses;
            if (coupon.getSpecific()) {
                // 1.1.限定了范围，查询券的可用范围
                List<CouponScope> list = couponScopeService.lambdaQuery()
                        .eq(CouponScope::getCouponId, coupon.getId())
                        .list();
                // 1.2.获取范围对应的分类id
                Set<Long> ids = list.stream().map(CouponScope::getBizId).collect(Collectors.toSet());
                // 1.3.筛选课程
                availableCourses = orderCourses.stream().filter(new Predicate<OrderCourseDTO>() {
                    @Override
                    public boolean test(OrderCourseDTO orderCourseDTO) {
                        return ids.contains((orderCourseDTO.getCateId()));
                    }
                }).collect(Collectors.toList());
            }
            if (CollUtils.isEmpty(availableCourses)) {
                // 没有任何可用课程，抛弃
                continue;
            }
            // 2.计算课程总价
            int totalAmount = availableCourses.stream().mapToInt(OrderCourseDTO::getPrice).sum();
            // 3.判断是否可用
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (discount.canUse(totalAmount, coupon)) {
                map.put(coupon, availableCourses);
            }

        }
        return map;
    }
}
