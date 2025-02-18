package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.constants.Constant;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.print.attribute.standard.PDLOverrideSupported;
import java.sql.Wrapper;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.databind.type.LogicalType.Map;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author lj
 * @since 2024-12-29
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    final IInteractionReplyService replyService;
    final UserClient userClient;
    final SearchClient searchClient;
    final CourseClient courseClient;
    final CatalogueClient catalogueClient;
    final CategoryCache categoryCache;
    @Override
    public void saveQuestion(QuestionFormDTO dto) {
        Long userId = UserContext.getUser();
        InteractionQuestion question = BeanUtils.copyBean(dto, InteractionQuestion.class);
        question.setUserId(userId);

        // 保存互动记录
        boolean result = this.save(question);
        if (!result) {
            throw new DbException("新增互动记录失败");
        }
    }

    @Override
    public void updateQuestion(Long id, QuestionFormDTO dto) {
        if (StringUtils.isBlank(dto.getTitle()) || StringUtils.isBlank(dto.getDescription()) || dto.getAnonymity() == null) {
            throw new BadRequestException("非法参数");
        }
        InteractionQuestion question = this.getById(id);
        if (question == null) {
            throw new BadRequestException("非法参数id");
        }
        if (UserContext.getUser().equals(id)) {
            throw new BadRequestException("不能修改别人的互动问题");
        }
        question.setTitle(dto.getTitle());
        question.setDescription(dto.getDescription());
        question.setAnonymity(dto.getAnonymity());
        this.updateById(question);
//        boolean update = this.lambdaUpdate().
//                eq(InteractionQuestion::getId, id)
//                .set(dto.getTitle() != null, InteractionQuestion::getTitle, dto.getTitle())
//                .set(dto.getTitle() != null, InteractionQuestion::getDescription, dto.getDescription())
//                .set(dto.getTitle() != null, InteractionQuestion::getAnonymity, dto.getAnonymity())
//                .update();
//        if (!update) {
//            throw new DbException("修改互动记录失败");
//        }
    }

    @Override
    public PageDTO<QuestionVO> queryQuestion(QuestionPageQuery query) {
        // 校验请求
        if (query.getCourseId() == null) {
            throw new BadRequestException("课程id不能为空");
        }
        Long userId = UserContext.getUser();
        // 管理员隐藏的不显示 在这里处理
        Page<InteractionQuestion> page = this.lambdaQuery()
//                .select(InteractionQuestion.class, new Predicate<TableFieldInfo>() {
//                    @Override
//                    public boolean test(TableFieldInfo tableFieldInfo) {
//                        return !tableFieldInfo.getProperty().equals("description");
//                    }
//                })
                .select(InteractionQuestion.class, c -> !c.getProperty().equals("description"))
                .eq(query.getOnlyMine(), InteractionQuestion::getUserId, userId)
                .eq(InteractionQuestion::getCourseId, query.getCourseId())
                .eq(query.getSectionId() != null, InteractionQuestion::getSectionId, query.getSectionId())
                .eq(InteractionQuestion::getHidden, false)
                .page(query.toMpPage(Constant.DATA_FIELD_NAME_CREATE_TIME, false));
        // 查最近回答的用户的信息
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 封装的过滤条件是有最近一次回答的用户  判断太多了！！！！用stream比较麻烦
        Set<Long> latestAnswerIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (InteractionQuestion record: records) {
            if (record.getLatestAnswerId() != null) {
                latestAnswerIds.add(record.getLatestAnswerId());
            }
            // 用户匿名不需要放到set
            if (!record.getAnonymity()) {
                userIds.add(record.getUserId());
            }
        }
//        Set<Long> latestAnswerIds = records.stream()
//                .filter(c -> c.getLatestAnswerId() != null)
//                .map(InteractionQuestion::getLatestAnswerId)
//                .collect(Collectors.toSet());
        Map<Long, InteractionReply> replyMap = new HashMap<>();
        if (CollUtils.isNotEmpty(latestAnswerIds)) {
            // 回答有可能被隐藏
//            List<InteractionReply> replyList = replyService.listByIds(latestAnswerIds);
            List<InteractionReply> replyList = replyService.list(Wrappers.<InteractionReply>lambdaQuery()
                    .in(InteractionReply::getId, latestAnswerIds)
                    .eq(InteractionReply::getHidden, false));
            for (InteractionReply reply: replyList) {
                replyMap.put(reply.getId(), reply);
                // 最新回答非匿名 加到用户set
                if (!reply.getAnonymity()) {
                    userIds.add(reply.getUserId());
                }
            }
//            replyMap = replyList.stream().collect(Collectors.toMap(InteractionReply::getId, c -> c));
        }
        // 封装用户ids集合
        // 封装的过滤条件是不是匿名的用户id 还需要放最新回答人的id 这个逻辑放在上面循环里面了
//        Set<Long> userIds = records.stream()
//                .filter(c -> !c.getAnonymity())
//                .map(InteractionQuestion::getUserId)
//                .collect(Collectors.toSet());
        // 查用户的信息
        List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> userDTOMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));

        List<QuestionVO> voList = new ArrayList<>();
        for (InteractionQuestion record: records) {
            QuestionVO vo = BeanUtils.copyBean(record, QuestionVO.class);
            if (!vo.getAnonymity()) {
                UserDTO userDTO = userDTOMap.get(record.getUserId());
                if (userDTO != null) {
                    vo.setUserName(userDTO.getName());
                    vo.setUserIcon(userDTO.getIcon());
                }
            }

            InteractionReply reply = replyMap.get(record.getLatestAnswerId());
            if (reply != null) {
                // 非匿名才设置最新回答人是谁 细节
                if (!reply.getAnonymity()) {
                    UserDTO userDTO = userDTOMap.get(reply.getUserId());
                    if (userDTO != null) {
                        vo.setLatestReplyUser(userDTO.getName());
                    }
                }
                // 最新回答内容
                vo.setLatestReplyContent(reply.getContent());
            }
            voList.add(vo);
        }


        return PageDTO.of(page,voList);
    }

    @Override
    public QuestionVO queryQuestionById(Long id) {
        if (id == null) {
            throw new BadRequestException("问题id不能为空");
        }
        InteractionQuestion question = this.lambdaQuery().eq(InteractionQuestion::getId, id).one();
        if (question == null) {
            throw new BadRequestException("问题id不存在");
        }
        if (question.getHidden() == true) {
            return null;
        }
        QuestionVO vo = BeanUtils.copyBean(question, QuestionVO.class);
        // 提问者昵称和头像
        if (!question.getAnonymity()) {
            UserDTO userDTO = userClient.queryUserById(question.getUserId());
            if (userDTO == null) {
                throw new BadRequestException("用户不存在");
            }
            vo.setUserName(userDTO.getName());
            vo.setUserIcon(userDTO.getIcon());
        }
        return vo;
    }

    @Override
    public PageDTO<QuestionAdminVO> queryQuestionAdmin(QuestionAdminPageQuery query) {
        // 远程调用es搜索关键字对应id
        String courseName = query.getCourseName();
        List<Long> cids = null;
        if (StringUtils.isNotBlank(courseName)) {
            cids = searchClient.queryCoursesIdByName(courseName);
            if (CollUtils.isEmpty(cids)) {
                return PageDTO.empty(0L,0L);
            }
        }
        // 分页查询
        Page<InteractionQuestion> page = this.lambdaQuery()
                .eq(query.getStatus() != null, InteractionQuestion::getStatus, query.getStatus())
                .between(query.getBeginTime() != null && query.getEndTime() != null, InteractionQuestion::getCreateTime, query.getBeginTime(), query.getEndTime())
                .in(CollUtils.isNotEmpty(cids), InteractionQuestion::getCourseId, cids)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        Set<Long> ids = new HashSet<>();
        Set<Long> courseIds = new HashSet<>();
        Set<Long> csIds = new HashSet<>();
        for (InteractionQuestion record: records) {
            ids.add(record.getUserId());
            courseIds.add(record.getCourseId());
            csIds.add(record.getChapterId());
            csIds.add(record.getSectionId());

        }
        // 用户信息
//        Set<Long> ids = records.stream().map(InteractionQuestion::getUserId).collect(Collectors.toSet());
        List<UserDTO> userDTOS = userClient.queryUserByIds(ids);
        if (userDTOS == null) {
            throw new BizIllegalException("用户不存在");
        }
        Map<Long, UserDTO> userDTOMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));

        // 课程信息
        List<CourseSimpleInfoDTO> simpleInfoList = courseClient.getSimpleInfoList(courseIds);
        if (simpleInfoList == null) {
            throw new BizIllegalException("课程不存在");
        }
        Map<Long, CourseSimpleInfoDTO> cinfoDTOMap = simpleInfoList.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        // 章节信息
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(csIds);
        if (cataSimpleInfoDTOS == null) {
            throw new BizIllegalException("章节不存在");
        }
        Map<Long, CataSimpleInfoDTO> cataSimpleInfoDTOMap = cataSimpleInfoDTOS.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, c -> c));


        List<QuestionAdminVO> voList = new ArrayList<>();
        for (InteractionQuestion record: records) {
            QuestionAdminVO vo = BeanUtils.copyBean(record, QuestionAdminVO.class);
            UserDTO userDTO = userDTOMap.get(record.getUserId());
            if (userDTO != null) {
                vo.setUserName(userDTO.getName());
            }
            CourseSimpleInfoDTO cinfoDTO = cinfoDTOMap.get(record.getCourseId());
            if (cinfoDTO != null) {
                vo.setCourseName(cinfoDTO.getName());
                // 分类信息 Caffeine 一二三级
                List<Long> categoryIds = cinfoDTO.getCategoryIds();
                String categoryNames = categoryCache.getCategoryNames(categoryIds);
                vo.setCategoryName(categoryNames);
            }
            CataSimpleInfoDTO cataSimpleInfoDTO = cataSimpleInfoDTOMap.get(record.getChapterId());
            if (cataSimpleInfoDTO != null) {
                vo.setChapterName(cataSimpleInfoDTO.getName());
                vo.setSectionName(cataSimpleInfoDTO.getName());
            }
            voList.add(vo);
        }
        return PageDTO.of(page, voList);
    }
}
