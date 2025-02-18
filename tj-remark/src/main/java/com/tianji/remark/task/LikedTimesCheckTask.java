package com.tianji.remark.task;

import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LikedTimesCheckTask {
    private static final List<String> BIZ_TYPES = List.of("QA", "NOTE");
    private static final int MAX_BIZ_SIZE = 30; // 任务每次取biz数量 防止一次向mq发送的消息过多

    private final ILikedRecordService recordService;

    // 每20s执行一次， 将redis中业务类型下面某业务的点赞总数发送消息到mq
    @Scheduled(fixedDelay = 40000)
    public void checkLikedTimes() {
        for (String bizType : BIZ_TYPES) {
            recordService.readLikedTimesAndSendMessage(bizType, MAX_BIZ_SIZE);
        }
    }
}
