package com.tianji.learning.controller;


import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.domain.vo.PointsBoardSeasonVO;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author lj
 * @since 2025-01-01
 */
@Api("赛季相关接口")
@RestController
@RequestMapping("/boards/seasons")
@AllArgsConstructor
public class PointsBoardSeasonController {

    private final IPointsBoardSeasonService pointsBoardSeasonService;

    @ApiOperation("查询赛季列表")
    @GetMapping("/list")
    // 这里返回的如果是PO类就可以之间调用list返回就行了
    public List<PointsBoardSeason> queryBoardSeason(){
        return pointsBoardSeasonService.list();
    }
}
