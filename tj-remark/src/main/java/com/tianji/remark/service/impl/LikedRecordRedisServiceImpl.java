package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.dto.LikedTimesDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author lj
 * @since 2024-12-30
 */
@Service
@RequiredArgsConstructor
public class LikedRecordRedisServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper rabbitMqHelper;
    final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public void addLikeRecord(LikeRecordFormDTO dto) {
        // 1.获取登录用户
        Long userId = UserContext.getUser();
        // 2.点赞或取消点赞
        boolean success = dto.getLiked() ? like(dto, userId) : unlike(dto, userId);
        if (!success) {
            return;
        }
        // 3.统计总点赞量
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
        Long totalLikesNum = redisTemplate.opsForSet().size(key);
        if (totalLikesNum == null) {
            return;
        }
        // 4.缓存点赞总是，zset
        String likedTimesKey = RedisConstants.LIKES_TIMES_KEY_PREFIX + dto.getBizType();
        redisTemplate.opsForZSet().add(likedTimesKey,dto.getBizId().toString(), totalLikesNum);

    }

    private boolean unlike(LikeRecordFormDTO dto, Long userId) {
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
        Long result = redisTemplate.opsForSet().remove(key, userId.toString());
        return result != null && result > 0;
    }

    private boolean like(LikeRecordFormDTO dto, Long userId) {
        // 写入redis  拼接key
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
        Long result = redisTemplate.opsForSet().add(key, userId.toString());
        return result != null && result > 0;
    }

    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {
        // 1.获取登录用户id
        Long userId = UserContext.getUser();
        if (CollUtils.isEmpty(bizIds)) {
            return CollUtils.emptySet();
        }
        // 2.循环
        Set<Long> retSet = new HashSet<>();
        for (Long bizId : bizIds) {
            String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + bizIds;
            // 判断改业务id的点赞用户集合中是否包含该用户
            Boolean member = redisTemplate.opsForSet().isMember(key, userId.toString());
            if (member) {
                retSet.add(bizId);
            }
        }
        // 3.返回结果
        return retSet;
    }

    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {
        // 1. 拼接key
        String likedTimesKey = RedisConstants.LIKES_TIMES_KEY_PREFIX + bizType;
        // 2. redis zset 按分数排序取 获取点赞信息 pop min
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().popMin(likedTimesKey, maxBizSize);
        if (CollUtils.isEmpty(tuples)) {
            return;
        }
        // 3. 封装dto数据
        List<LikedTimesDTO> list = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String bizId = tuple.getValue();
            Double likedTimes = tuple.getScore();
            if (bizId == null || likedTimes == null) {
                continue;
            }
            list.add(LikedTimesDTO.of(Long.valueOf(bizId), likedTimes.intValue()));
        }
        // 发消息 QA.times.changed
        if (CollUtils.isNotEmpty(list)) {
            String routingKey = StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, bizType);
            log.debug("发送MQ消息");
            rabbitMqHelper.send(
                    MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
                    routingKey,
                    list);
        }
    }
}
