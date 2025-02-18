package com.tianji.learning.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
//import com.tianji.api.client.remark.RemarkClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_CREATE_TIME;
import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_LIKED_TIME;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author lj
 * @since 2024-12-29
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    final InteractionQuestionMapper questionMapper;
    final UserClient userClient;
//    final RemarkClient remarkClient;

    @Override
    public void saveReplies(ReplyDTO dto) {
        Long userId = UserContext.getUser();
        InteractionReply reply = BeanUtils.copyBean(dto, InteractionReply.class);
        reply.setUserId(userId);
        this.save(reply);
        InteractionQuestion question = questionMapper.selectById(dto.getQuestionId());
        if (dto.getAnswerId() == null) {
            // 是一个回答

//            int rows = questionMapper.update(
//                    null,
//                    Wrappers.lambdaUpdate(InteractionQuestion.class)
//                            .eq(InteractionQuestion::getId, dto.getQuestionId())
//                            .set(InteractionQuestion::getLatestAnswerId, reply.getId())
//                            .setSql("answer_times = answer_times + 1")
//            );
//            if (rows == 0) {
//                throw new DbException("回答更新问题表失败");
//            }
            question.setLatestAnswerId(reply.getId());
            question.setAnswerTimes(question.getAnswerTimes() + 1);
        } else {
            //是一个评论
            boolean update = this.lambdaUpdate()
                    .set(InteractionReply::getAnswerId, dto.getAnswerId())
                    .setSql("reply_times = reply_times + 1")
                    .update();
            if (!update) {
                throw new DbException("评论更新回答表失败");
            }
        }
        if(dto.getIsStudent() == true) {
//            int rows = questionMapper.update(
//                    null,
//                    Wrappers.lambdaUpdate(InteractionQuestion.class)
//                            .eq(InteractionQuestion::getId, dto.getQuestionId())
//                            .set(InteractionQuestion::getStatus, QuestionStatus.UN_CHECK)
//            );
//            if (rows == 0) {
//                throw new DbException("更新标记状态失败");
//            }
            question.setStatus(QuestionStatus.UN_CHECK);
        }
        questionMapper.updateById(question);

    }

    @Override
    public PageDTO<ReplyVO> queryReplies(ReplyPageQuery pageQuery) {
        // 校验问题id和回答id是否都为空
        if (pageQuery.getAnswerId() == null && pageQuery.getQuestionId() == null) {
            throw new BadRequestException("查询参数错误");
        }
        // 分页查询回答或评论列表
        Page<InteractionReply> replyPage = this.lambdaQuery()
                // 如果传问题id就把问题id作查询条件
                .eq(pageQuery.getQuestionId() != null, InteractionReply::getQuestionId, pageQuery.getQuestionId())
                .eq(InteractionReply::getAnswerId, pageQuery.getAnswerId() == null ? 0L : pageQuery.getAnswerId())     // 字段默认值0
                .eq(InteractionReply::getHidden, false)  // 未被管理员隐藏的
                .page(pageQuery.toMpPage(new OrderItem(DATA_FIELD_NAME_LIKED_TIME, false),
                        new OrderItem(DATA_FIELD_NAME_CREATE_TIME, true))); // 按照点赞次数降序排序降序，创建时间升序排序
        List<InteractionReply> records = replyPage.getRecords();
        if (CollUtil.isEmpty(records)) { // 查询不到，返回空集
            return PageDTO.of(replyPage, Collections.emptyList());
        }
        // 关联用户信息，先收集用户id，封装到map
        List<Long> userIds = new ArrayList<>();
        List<Long> targetUserIds = new ArrayList<>();   // 目标用户id
        List<Long> targetReplyIds = new ArrayList<>();   // 目标回复id
        List<Long> replyIds = new ArrayList<>();    // 回答或评论id
        for (InteractionReply reply : records) {
            if (!reply.getAnonymity()) { // 非匿名用户需要查询
                userIds.add(reply.getUserId());
                // userIds.add(reply.getTargetUserId());
            }
            // "target_user_id"字段默认值为0，查询评论时生效
            if (reply.getTargetUserId() != null && reply.getTargetUserId() > 0) {
                targetUserIds.add(reply.getTargetUserId());
            }
            // "target_reply_id"字段默认值为0，查询评论时生效
            if (reply.getTargetReplyId() != null && reply.getTargetReplyId() > 0) {
                targetReplyIds.add(reply.getTargetReplyId());
            }
            replyIds.add(reply.getId());
        }
        // 查询目标回复列表并封装为Map
        Map<Long, InteractionReply> targetReplyMap = new HashMap<>();
        // targetReplyIds不为空，去查询数据库
        if (!CollUtil.isEmpty(targetReplyIds)) {
            // 查询目标评论，并封装为Map
            List<InteractionReply> targetReplies = listByIds(targetReplyIds);
            targetReplyMap = targetReplies.stream().collect(Collectors.toMap(InteractionReply::getId, reply -> reply));

        }
        // 查询用户和目标回复用户并封装为Map
        Map<Long, UserDTO> userMap = getUserDTOMap(userIds);
        Map<Long, UserDTO> targetUserMap = getUserDTOMap(targetUserIds);
        // 查询用户是否点赞
//        Set<Long> likedBizIds = remarkClient.isBizLiked(replyIds);
        // 保存结果
        List<ReplyVO> replyVOS = new ArrayList<>();
        for (InteractionReply reply : records) {
            ReplyVO replyVO = BeanUtil.toBean(reply, ReplyVO.class);
            UserDTO userDTO = userMap.getOrDefault(reply.getUserId(), null);
            // 如果当前回答或评论匿名，不进行赋值
            if (!replyVO.getAnonymity() && userDTO != null) {
                replyVO.setUserIcon(userDTO.getIcon()); // 回答人头像
                replyVO.setUserName(userDTO.getName()); // 回答人昵称
                replyVO.setUserType(userDTO.getType()); // 回答人类型
            }
            UserDTO targetUserDTO = targetUserMap.getOrDefault(reply.getTargetUserId(), null);
            InteractionReply targetReply = targetReplyMap.getOrDefault(reply.getTargetReplyId(), null);
            // 如果目标评论匿名，不进行赋值
            if (targetReply != null && !targetReply.getAnonymity() && targetUserDTO != null) {    // 目标回复非匿名才赋值
                replyVO.setTargetUserName(targetUserDTO.getName()); // 目标用户昵称
            }
            // 判断当前用户是否点赞
//            replyVO.setLiked(likedBizIds.contains(reply.getId()));
            replyVOS.add(replyVO);
        }
        // 返回结果
        return PageDTO.of(replyPage, replyVOS);
    }

    // feign远程调用：根据userId查询userDTO并封装为Map
    private Map<Long, UserDTO> getUserDTOMap(List<Long> userIds) {
        // feign远程调用，查询用户信息
        List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
        if (!CollUtil.isEmpty(userDTOS)) {
            // 封装到map
            return userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, userDTO -> userDTO));
        } else {
            // 否则返回空map
            return new HashMap<>();
        }

    }

//    @Override
//    public PageDTO<ReplyVO> queryReplies(ReplyPageQuery query) {
//        if (query.getAnswerId() == null && query.getQuestionId() == null) {
//            throw new BadRequestException("分页请求参数错误");
//        }
//        Page<InteractionReply> replyPage = this.lambdaQuery()
//                .eq(query.getQuestionId() != null, InteractionReply::getQuestionId, query.getQuestionId())
//                // 前端不传的话是0 改
//                .eq(InteractionReply::getAnswerId, query.getAnswerId() == null ? 0L : query.getAnswerId())
//                .eq(InteractionReply::getHidden, false)
//                .page(query.toMpPage( // 改 先根据点赞排序，点赞相同再根据时间排序
//                        new OrderItem("liked_times", false),
//                        new OrderItem("create_time", false)));
//        log.info("查完了分页查询");
//        List<InteractionReply> records = replyPage.getRecords();
//        if (BeanUtils.isEmpty(records)) {
//            log.info("没查到records");
//            return PageDTO.empty(0L, 0L);
//        }
////        Set<Long> uIds = records.stream().map(InteractionReply::getUserId).collect(Collectors.toSet());
//        Set<Long> uIds = new HashSet<>();
//        for (InteractionReply record: records) {
//            // 改 记录不匿名才查
//            if (!record.getAnonymity()) {
//                uIds.add(record.getUserId());
//                uIds.add(record.getTargetUserId());
//            }
//
//        }
//        List<UserDTO> userDTOS = userClient.queryUserByIds(uIds);
//        Map<Long, UserDTO> userDTOMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));
//        List<ReplyVO> voList = new ArrayList<>();
//        log.info("查完了用户信息");
//        for (InteractionReply record: records) {
//            ReplyVO vo = BeanUtils.copyBean(record, ReplyVO.class);
//            if (!record.getAnonymity()) {
//                UserDTO userDTO = userDTOMap.get(record.getUserId());
//                if (userDTO == null) {
//                    log.info("没查看用户id的信息");
//                    throw new DbException("没查看用户id的信息");
//                }
//                if (BeanUtils.isNotEmpty(userDTO)) {
//                    vo.setUserName(userDTO.getName());
//                    vo.setUserIcon(userDTO.getIcon());
//                    vo.setUserType(userDTO.getType());
//                }
//            }
//            UserDTO userDTOt = userDTOMap.get(record.getTargetReplyId());
//            if (userDTOt == null) {
//                throw new DbException("没查看目标用户id的信息");
//            }
//            if (BeanUtils.isNotEmpty(userDTOt)) {
//                vo.setTargetUserName(userDTOt.getName());
//            }
//            voList.add(vo);
//        }
//        return PageDTO.of(replyPage, voList);
//    }
}
