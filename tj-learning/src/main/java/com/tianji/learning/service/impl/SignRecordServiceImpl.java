package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BooleanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.msg.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService {

    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;
    @Override
    public SignResultVO addSignRecords() {
        // 1.签到
        // 1.1.获取登录用户
        Long userId = UserContext.getUser();
        // 1.2.获取日期
        LocalDate now = LocalDate.now();
        // 1.3.拼接key
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX
                + userId.toString()
                + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
//                + now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        // 1.4.计算offset
        int offset = now.getDayOfMonth() - 1; // 天数 - 1
        // 1.5.保存签到信息 返回的是原来的值
        Boolean exists = redisTemplate.opsForValue().setBit(key, offset, true);
        if (BooleanUtils.isTrue(exists)) {
            throw new BizIllegalException("不允许重复签到！");
        }
        // 2.计算连续签到天数 本月目前为止天数
        int signDays = countSignDays(key, now.getDayOfMonth());
        // 3.计算签到得分
        int rewardPoints = 0;
        switch (signDays) {
            case 7:
                rewardPoints = 10;
                break;
            case 14:
                rewardPoints = 20;
                break;
            case 28:
                rewardPoints = 40;
                break;
        }
        // TODO 4.保存积分明细记录 发送消息到mq
        mqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(userId, rewardPoints + 1));// 签到积分是基本得分+奖励积分

        // 5.封装返回
        SignResultVO vo = new SignResultVO();
        vo.setSignDays(signDays);
        vo.setRewardPoints(rewardPoints);
        return vo;
    }

    @Override
    public Byte[] querySignRecords() {
        // key
        Long userId = UserContext.getUser();
        LocalDate now = LocalDate.now();
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX
                + userId.toString()
                + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        // redis取签到记录 BitField key get u天数 0
        List<Long> result = redisTemplate.opsForValue()
                .bitField(key, BitFieldSubCommands.create().get(
                        BitFieldSubCommands.BitFieldType.unsigned(now.getDayOfMonth())).valueAt(0));
        if (CollUtils.isEmpty(result)) {
            return new Byte[0];
        }
        int num = result.get(0).intValue();
        int offset = now.getDayOfMonth() - 1;
        Byte[] arr = new Byte[now.getDayOfMonth()];
        // 给数组赋值 最后一天是否签到 倒着赋值
        while (offset >= 0) {
            arr[offset] = (byte)(num & 1);
            offset --;
            num = num >>> 1;
        }
        // 与和位运算封装
        return arr;
    }

    private int countSignDays(String key, int len) {
        // 从今天开始，向前统计，直到遇到第一次未签到为止，计算总的签到次数，就是  连续签到天数
        // 1.获取本月从第一天开始，到今天为止的所有签到记录
        List<Long> result = redisTemplate.opsForValue()
                .bitField(key, BitFieldSubCommands.create().get(
                        BitFieldSubCommands.BitFieldType.unsigned(len)).valueAt(0));
        if (CollUtils.isEmpty(result)) {
            return 0;
        }
        int num = result.get(0).intValue();
        // 2.定义一个计数器
        int count = 0;
        // 3.循环，与1做与运算，得到最后一个bit，判断是否为0，为0则终止，为1则继续
        while ((num & 1) == 1) {
            // 4.计数器+1
            count++;
            // 5.把数字右移一位，最后一位被舍弃，倒数第二位成了最后一位
            num >>>= 1;
        }
        return count;
    }

}
