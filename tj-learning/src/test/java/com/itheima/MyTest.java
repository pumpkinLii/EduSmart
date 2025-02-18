package com.itheima;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.LearningApplication;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import groovyjarjarantlr4.v4.codegen.model.LL1AltBlock;
import lombok.AllArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Wrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest(classes = LearningApplication.class)
public class MyTest {
    @Autowired
    ILearningLessonService lessonService;
    @Autowired
    IPointsBoardService pointsBoardService;

    @Test
    public void test11() {
        TableInfoContext.setInfo("points_board3");

        PointsBoard board = new PointsBoard();
        board.setId(1L);
        board.setUserId(2L);
        board.setPoints(100);

        pointsBoardService.save(board);

    }

    @Test
    public void test1() {
        Page<LearningLesson> page = new Page<>(1, 2);
        LambdaQueryWrapper<LearningLesson> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningLesson::getUserId, 2);
        wrapper.orderByDesc(LearningLesson::getLatestLearnTime);
        lessonService.page(page, wrapper);
        System.out.println("total" + page.getTotal());
        System.out.println("page" + page.getPages());
        List<LearningLesson> records = page.getRecords();
        for (LearningLesson record : records) {
            System.out.println(record);
        }

    }

    @Test
    public void test2() {
        Page<LearningLesson> page = new Page<>(1, 2);
        LambdaQueryWrapper<LearningLesson> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningLesson::getUserId, 2);
        wrapper.orderByDesc(LearningLesson::getLatestLearnTime);
        lessonService.lambdaQuery()
                .eq(LearningLesson :: getUserId, 2)
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .page(page);
        System.out.println("total" + page.getTotal());
        System.out.println("page" + page.getPages());
        List<LearningLesson> records = page.getRecords();
        for (LearningLesson record : records) {
            System.out.println(record);
        }

    }

    @Test
    public void test3() {
        Page<LearningLesson> page = new Page<>(1, 2);
        List<OrderItem> itemList = new ArrayList<>();
        OrderItem item = new OrderItem();
        item.setColumn("latest_learn_time");
        item.setAsc(false);
        itemList.add(item);
        page.addOrder(itemList);

        LambdaQueryWrapper<LearningLesson> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningLesson::getUserId, 2);
//        wrapper.orderByDesc(LearningLesson::getLatestLearnTime);
        lessonService.page(page, wrapper);
        System.out.println("total" + page.getTotal());
        System.out.println("page" + page.getPages());
        List<LearningLesson> records = page.getRecords();
        for (LearningLesson record : records) {
            System.out.println(record);
        }

    }

    @Test
    public void test4() {
        Page<LearningLesson> page = new Page<>(1, 2);
        List<OrderItem> itemList = new ArrayList<>();
        OrderItem item = new OrderItem();
        item.setColumn("latest_learn_time");
        item.setAsc(false);
        itemList.add(item);
        page.addOrder(itemList);

        LambdaQueryWrapper<LearningLesson> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningLesson::getUserId, 2);
//        wrapper.orderByDesc(LearningLesson::getLatestLearnTime);
        lessonService.lambdaQuery()
                .eq(LearningLesson :: getUserId, 2)
                .page(page);
        System.out.println("total" + page.getTotal());
        System.out.println("page" + page.getPages());
        List<LearningLesson> records = page.getRecords();
        for (LearningLesson record : records) {
            System.out.println(record);
        }

    }

    @Test
    public void test5() {
        PageQuery query = new PageQuery();
        query.setPageNo(1);
        query.setPageSize(2);
        Page<LearningLesson> page = lessonService.lambdaQuery()
                .eq(LearningLesson::getUserId, 2)
                .page(query.toMpPage("latest_learn_time", false));
        System.out.println("total" + page.getTotal());
        System.out.println("page" + page.getPages());

        List<LearningLesson> records = page.getRecords();
        for (LearningLesson record : records) {
            System.out.println(record);
        }

    }

    @Test
    public void test6() {
        List<LearningLesson> list = new ArrayList<>();
        list.stream().map(LearningLesson::getId).collect(Collectors.toList());
        list.stream().collect(Collectors.toMap(LearningLesson::getId,c -> c));

    }
}
