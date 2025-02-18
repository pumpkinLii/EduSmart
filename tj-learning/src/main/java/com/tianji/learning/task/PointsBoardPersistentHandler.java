package com.tianji.learning.task;

import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {
    private final IPointsBoardSeasonService seasonService;

    private final IPointsBoardService pointsBoardService;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(cron = "0 40 20 6 1 ?") // 每月1号，凌晨3点执行
//    @XxlJob("createTableJob")
    public void createPointsBoardTableOfLastSeason() {
        // 1.获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 2.查询赛季id
        PointsBoardSeason one = seasonService.lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time) // 开始时间《=time
                .ge(PointsBoardSeason::getEndTime, time)
                .one();
        if (one == null) {
            // 赛季不存在
            return;
        }
        // 3.创建表
        pointsBoardService.createPointsBoardTableBySeason(one.getId());
    }

    // 持久化上赛季数据到赛季表
    @Scheduled(cron = "0 06 23 6 1 ?")
    public void savePointsBoard2DB(){
        // 1.获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);

        // 2.计算动态表名
        // 2.1.查询上赛季信息
        PointsBoardSeason one = seasonService.lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time) // 开始时间《=time
                .ge(PointsBoardSeason::getEndTime, time)
                .one();
        if (one == null) {
            // 赛季不存在
            return;
        }
        // 2.2.将表名存入ThreadLocal
        TableInfoContext.setInfo("points_board" + one.getId());

        // 3.查询榜单数据
        // 3.1.拼接KEY
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateTimeFormatter.ofPattern("yyyyMM"));
        // 3.2.分页查询数据
        int pageNo = 1;
        int pageSize = 1000;
        while (true) {
            List<PointsBoard> boardList = pointsBoardService.queryCurrentBoard(key, pageNo, pageSize);
            if (CollUtils.isEmpty(boardList)) {
                // 查不到数据 退出 跳出循环
                break;
            }
            // 4.持久化到数据库 新表字段比原表少 原来这个方法给rank赋值了
            // 4.1. 处理数据
            for (PointsBoard board : boardList) {
                board.setId(Long.valueOf(board.getRank()));   // 把排名信息写入id
                board.setRank(null);    // 清空rank
            }
            // 4.2.持久化
            pointsBoardService.saveBatch(boardList);
            // 5.翻页
            pageNo++;
        }
        // 任务结束，移除ThreadLocal动态表名
        TableInfoContext.remove();
    }

//    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis(){
        // 1.获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 2.计算key
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateTimeFormatter.ofPattern("yyyyMM"));
        // 3.删除
        redisTemplate.unlink(key);
    }


//    @XxlJob("createTableJob") 实现不了
    public void savePointsBoard3DB(){
        // 1.获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);

        // 2.计算动态表名
        // 2.1.查询上赛季信息
        PointsBoardSeason one = seasonService.lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time) // 开始时间《=time
                .ge(PointsBoardSeason::getEndTime, time)
                .one();
        if (one == null) {
            // 赛季不存在
            return;
        }
        // 2.2.将表名存入ThreadLocal
        TableInfoContext.setInfo("points_board" + one.getId());



        // 3.查询榜单数据
        // 3.1.拼接KEY
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateTimeFormatter.ofPattern("yyyyMM"));

        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();

        // 3.2.分页查询数据
        int pageNo = shardIndex + 1;    // 修改
        int pageSize = 1000;
        while (true) {
            List<PointsBoard> boardList = pointsBoardService.queryCurrentBoard(key, pageNo, pageSize);
            if (CollUtils.isEmpty(boardList)) {
                // 查不到数据 退出 跳出循环
                break;
            }
            // 4.持久化到数据库 新表字段比原表少 原来这个方法给rank赋值了
            // 4.1. 处理数据
            for (PointsBoard board : boardList) {
                board.setId(Long.valueOf(board.getRank()));   // 把排名信息写入id
                board.setRank(null);    // 清空rank
            }
            // 4.2.持久化
            pointsBoardService.saveBatch(boardList);
            // 5.翻页
            pageNo = pageNo + shardTotal;   // 修改
        }
        // 任务结束，移除ThreadLocal动态表名
        TableInfoContext.remove();
    }
}
