package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsBoard;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardVO;

import java.util.List;

/**
 * <p>
 * 学霸天梯榜 服务类
 * </p>
 *
 * @author lj
 * @since 2025-01-01
 */
public interface IPointsBoardService extends IService<PointsBoard> {

    PointsBoardVO queryPointsBoardBySeason(PointsBoardQuery query);
    // 创建上赛季的表
    void createPointsBoardTableBySeason(Integer id);
    // 分页查询赛季
    public List<PointsBoard> queryCurrentBoard(String key, Integer pageNo, Integer pageSize);
}
