package org.dromara.resource.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.dromara.resource.domain.BizProduct;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 产品信息视图对象
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Data
@AutoMapper(target = BizProduct.class)
public class BizProductVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "关联部门ID")
    private Long deptId;

    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "产品型号")
    private String productModel;

    @Schema(description = "数量")
    private Integer quantity;

    @Schema(description = "性能说明")
    private String performanceDesc;

    @Schema(description = "投入使用开始时间")
    private Date useStartDate;

    @Schema(description = "投入使用结束时间")
    private Date useEndDate;

    @Schema(description = "是否有购买合同（0否 1是）")
    private String hasPurchaseContract;

    @Schema(description = "产品分类")
    private String productCategory;

    @Schema(description = "实物图片")
    private String productImage;

    @Schema(description = "相关图片")
    private String relatedImages;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建人")
    private Long createBy;

    @Schema(description = "创建时间")
    private Date createTime;

}
