package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.mq.msg.SignInMessage;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author lj
 * @since 2025-01-01
 */
@Service
@AllArgsConstructor
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {

    final StringRedisTemplate redisTemplate;
    @Override
    public void addPointsRecord(SignInMessage message, PointsRecordType type) {
        LocalDateTime now = LocalDateTime.now();
        int maxPoints = type.getMaxPoints();
        // 1.判断当前方式有没有积分上限
        int realPoints = message.getPoints();
        if(maxPoints > 0) {
            // 2.有，则需要判断是否超过上限
            LocalDateTime begin = DateUtils.getDayStartTime(now);
            LocalDateTime end = DateUtils.getDayEndTime(now);
            // 2.1.查询今日已得积分
            QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
            wrapper.select("sum(points) as totalPoints");
            wrapper.eq("user_id", message.getUserId());
            wrapper.eq("type", type);
            wrapper.between("create_time", begin, end);
            Map<String, Object> map = this.getMap(wrapper);
            int currentPoints = 0;
            if (map != null) {
                BigDecimal totalPoints = (BigDecimal)map.get("totalPoints");
                currentPoints = totalPoints.intValue();
            }
            // 2.2.判断是否超过上限
            if(currentPoints >= maxPoints) {
                // 2.3.超过，直接结束
                return;
            }
            // 2.4.没超过，保存积分记录
            if(currentPoints + realPoints > maxPoints){
                realPoints = maxPoints - currentPoints;
            }
        }
        // 3.直接保存积分记录
        PointsRecord p = new PointsRecord();
        p.setPoints(realPoints);
        p.setUserId(message.getUserId());
        p.setType(type);
        save(p);

        // 更新总积分到redis中，zset 当前赛季(实时)排行榜
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        redisTemplate.opsForZSet().incrementScore(key, message.getUserId().toString(), realPoints);

    }

    /**
     * 查询我的今日积分
     * @return
     */
    @Override
    public List<PointsStatisticsVO> queryMyPointsToday() {
        // 1.获取用户
        Long userId = UserContext.getUser();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime begin = DateUtils.getDayStartTime(now);
        LocalDateTime end = DateUtils.getDayEndTime(now);
        // 3.构建查询条件 group by
        QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
        wrapper.select("type, sum(points) as points");
        wrapper.eq("userId", userId);
        wrapper.between("create_time", begin, end);
        wrapper.groupBy("type");
        List<PointsRecord> list = this.list(wrapper);
        // 4.查询

        if (CollUtils.isEmpty(list)) {
            return CollUtils.emptyList();
        }
        // 5.封装返回
        List<PointsStatisticsVO> voList = new ArrayList<>(list.size());
        for (PointsRecord p : list) {
            PointsStatisticsVO vo = new PointsStatisticsVO();
            vo.setType(p.getType().getDesc()); // 积分类型的中文
            vo.setMaxPoints(p.getType().getMaxPoints()); // 积分类型的上限
            vo.setPoints(p.getPoints());
            voList.add(vo);
        }
        return voList;
    }
}
