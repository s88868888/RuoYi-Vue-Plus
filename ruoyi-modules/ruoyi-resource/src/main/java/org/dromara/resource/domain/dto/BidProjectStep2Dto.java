package org.dromara.resource.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 招标项目第二步DTO - AI分析
 *
 * @author ruoyi
 * @date 2026-02-24
 */
@Data
@Schema(description = "招标项目AI分析")
public class BidProjectStep2Dto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "项目ID", required = true)
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @Schema(description = "AI分析提示词（不填使用默认提示词）")
    private String aiPrompt;

    @Schema(description = "是否异步分析（true异步 false同步）")
    private Boolean async = true;

}
