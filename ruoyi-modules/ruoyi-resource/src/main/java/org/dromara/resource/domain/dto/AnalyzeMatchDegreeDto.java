package org.dromara.resource.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 契合度分析DTO
 *
 * @author ruoyi
 * @date 2026-02-27
 */
@Data
public class AnalyzeMatchDegreeDto {

    @Schema(description = "项目ID")
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @Schema(description = "是否异步执行")
    private Boolean async = true;

    @Schema(description = "契合度分析提示词（可选）")
    private String aiPrompt;
}
