package com.tianji.remark.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@Builder
@ApiModel(description = "点赞记录表单实体")
public class LikeRecordFormDTO {
    @ApiModelProperty("点赞业务id")
    @NotNull(message = "业务id不能为空")
    private Long bizId;

    @ApiModelProperty("点赞业务类型")
    @NotNull(message = "业务类型不能为空")
    private String bizType;

    @ApiModelProperty("是否点赞，true：点赞；false：取消点赞")
    @NotNull(message = "是否点赞不能为空")
    private Boolean liked;
}
