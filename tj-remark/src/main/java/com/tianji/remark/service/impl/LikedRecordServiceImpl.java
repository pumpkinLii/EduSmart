//package com.tianji.remark.service.impl;
//
//import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
//import com.tianji.common.constants.MqConstants;
//import com.tianji.common.utils.StringUtils;
//import com.tianji.common.utils.UserContext;
//import com.tianji.remark.domain.dto.LikeRecordFormDTO;
//import com.tianji.remark.domain.dto.LikedTimesDTO;
//import com.tianji.remark.domain.po.LikedRecord;
//import com.tianji.remark.mapper.LikedRecordMapper;
//import com.tianji.remark.service.ILikedRecordService;
//import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.List;
//import java.util.Set;
//import java.util.stream.Collectors;
//
///**
// * <p>
// * 点赞记录表 服务实现类
// * </p>
// *
// * @author lj
// * @since 2024-12-30
// */
//@Service
//@RequiredArgsConstructor
//public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {
//
//    private final RabbitMqHelper rabbitMqHelper;
//
//    @Override
//    @Transactional
//    public void addLikeRecord(LikeRecordFormDTO formDTO) {
//        // 1.获取登录用户
//        Long userId = UserContext.getUser();
//        // 2.点赞或取消点赞
//        boolean success = formDTO.getLiked() ? like(formDTO, userId) : unlike(formDTO, userId);
//        if (!success) {
//            return;
//        }
//        // 3.更新点赞数量
//        // 3.如果执行成功，统计点赞总数
//        Integer likedTimes = lambdaQuery()
//                .eq(LikedRecord::getBizId, formDTO.getBizId())
//                .count();
//        // 4.发送MQ通知
//        LikedTimesDTO msg = LikedTimesDTO.of(formDTO.getBizId(), likedTimes);
//        log.debug("发送MQ消息");
//        rabbitMqHelper.send(
//                MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
//                StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, formDTO.getBizType()),
//                msg);
//
//    }
//
//
//
//    private boolean unlike(LikeRecordFormDTO formDTO, Long userId) {
//        // 1.查询
//        LikedRecord record = this.lambdaQuery()
//                .eq(LikedRecord::getUserId, userId)
//                .eq(LikedRecord::getBizId, formDTO.getBizId())
//                .one();
//        if (record == null) {
//            // 没点过赞，无需处理
//            return false;
//        }
//        boolean result = this.removeById(record.getId());
//        return result;
//
//    }
//
//    private boolean like(LikeRecordFormDTO formDTO, Long userId) {
//        // 1.查询
//        Integer count = this.lambdaQuery()
//                .eq(LikedRecord::getUserId, userId)
//                .eq(LikedRecord::getBizId, formDTO.getBizId())
//                .count();
//        if (count > 0) {
//            // 已点赞，无需处理
//            return false;
//        }
//        // 2.新增点赞
//        LikedRecord r = new LikedRecord();
//        r.setUserId(userId);
//        r.setBizId(formDTO.getBizId());
//        r.setBizType(formDTO.getBizType());
//        return save(r);
//    }
//
//    @Override
//    public Set<Long> isBizLiked(List<Long> bizIds) {
//        // 1.获取登录用户id
//        Long userId = UserContext.getUser();
//        // 2.查询点赞状态
//        List<LikedRecord> list = lambdaQuery()
//                .in(LikedRecord::getBizId, bizIds)
//                .eq(LikedRecord::getUserId, userId)
//                .list();
//        // 3.返回结果
//        return list.stream().map(LikedRecord::getBizId).collect(Collectors.toSet());
//    }
//}
