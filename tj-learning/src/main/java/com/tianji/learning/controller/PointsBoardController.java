package com.tianji.learning.controller;


import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.service.IPointsBoardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 学霸天梯榜 前端控制器
 * </p>
 *
 * @author lj
 * @since 2025-01-01
 */
@RestController
@RequestMapping("/boards")
@Api(tags = "积分榜")
@AllArgsConstructor
public class PointsBoardController {
    final IPointsBoardService pointsBoardService;

    @ApiOperation("查询积分榜")
    @GetMapping
    public PointsBoardVO queryPointsBoardBySeason (PointsBoardQuery query) {
        return pointsBoardService.queryPointsBoardBySeason(query);
    }


}
