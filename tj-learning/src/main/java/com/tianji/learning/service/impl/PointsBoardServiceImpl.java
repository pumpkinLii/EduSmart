package com.tianji.learning.service.impl;

import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.*;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tianji.learning.constants.LearningConstants.POINTS_BOARD_TABLE_PREFIX;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author lj
 * @since 2025-01-01
 */
@Service
@AllArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {
    final StringRedisTemplate redisTemplate;
    final UserClient userClient;

    @Override
    public PointsBoardVO queryPointsBoardBySeason(PointsBoardQuery query) {
        boolean isCurrent = query.getSeason() == null || query.getSeason() == 0;
        // 当前赛季
        String prefix = RedisConstants.POINTS_BOARD_KEY_PREFIX;
        String key;
        LocalDate now = LocalDate.now();
        key = prefix + now.format(DateTimeFormatter.ofPattern("yyyyMM"));

        PointsBoard board = isCurrent ? queryMyCurrentBoard(key) : queryMyHistoryBoard(query.getSeason());
        List<PointsBoard> list= isCurrent ? queryCurrentBoard(key, query.getPageNo(), query.getPageSize()) : queryHistoryBoard(query);
        // 历史赛季
        PointsBoardVO vo = new PointsBoardVO();
        vo.setRank(board.getRank());
        vo.setPoints(board.getPoints());

        // 得到姓名
        Set<Long> uIds = list.stream().map(PointsBoard::getUserId).collect(Collectors.toSet());
        List<UserDTO> userDTOS = userClient.queryUserByIds(uIds);
        if (CollUtils.isEmpty(userDTOS)) {
            throw new BizIllegalException("用户不存在");
        }
        Map<Long, String> map = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c.getName()));

        List<PointsBoardItemVO> boardList = new ArrayList<>();
        for (PointsBoard pointsBoard : list) {
            PointsBoardItemVO boardItemVO = BeanUtils.copyBean(pointsBoard, PointsBoardItemVO.class);
            boardItemVO.setName(map.get(pointsBoard.getUserId()));
            boardList.add(boardItemVO);
        }
        vo.setBoardList(boardList);

        return vo;
    }



    private List<PointsBoard> queryHistoryBoard(PointsBoardQuery query) {
        // todo
        return null;
    }

    // 查询当前赛季列表 redis
    public List<PointsBoard> queryCurrentBoard(String key, Integer pageNo, Integer pageSize) {
        // 分数
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, (pageNo - 1) * pageSize, (pageNo - 1) * pageSize + pageSize - 1);
        if (CollUtils.isEmpty(typedTuples)) {
            return CollUtils.emptyList();
        }
        List<PointsBoard> boardsList = new ArrayList<>();
        int rank = (pageNo - 1) * pageSize + 1;  // 重点关注
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            Double score = typedTuple.getScore();
            String value = typedTuple.getValue();
            if (StringUtils.isBlank(value) || score == null) {
                continue;
            }
            PointsBoard board = new PointsBoard();
            board.setPoints(score.intValue());
            board.setUserId(Long.valueOf(value));
            board.setRank(rank++);  // 先使用再加
            boardsList.add(board);
        }
        return  boardsList;
    }

    private PointsBoard queryMyHistoryBoard(Long season) {
        // todo
        return null;
    }

    // 查询当前赛季 redis
    private PointsBoard queryMyCurrentBoard(String key) {
        Long userId = UserContext.getUser();
        // 分数
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());
        // 排名
        Long rank = redisTemplate.opsForZSet().reverseRank(key, userId.toString());

        PointsBoard board = new PointsBoard();
        board.setRank(rank == null ? 0: rank.intValue() + 1);
        board.setPoints(score == null ? 0: score.intValue());

        return board;
    }

    // 创建当前赛季表
    @Override
    public void createPointsBoardTableBySeason(Integer id) {
        getBaseMapper().createPointsBoardTable(POINTS_BOARD_TABLE_PREFIX + id);
    }
}
