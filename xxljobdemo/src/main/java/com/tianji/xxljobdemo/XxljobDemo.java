package com.tianji.xxljobdemo;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class XxljobDemo {
    @XxlJob("xxltest")
    public void test() {
        log.debug("xxljob执行了");
    }

}
