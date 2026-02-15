package org.dromara.resource.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.resource.domain.BizProduct;

import java.io.Serial;
import java.util.Date;

/**
 * 产品信息业务对象
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BizProduct.class, reverseConvertGenerate = false)
public class BizProductBo extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 关联部门ID
     */
    private Long deptId;

    /**
     * 产品名称
     */
    @NotBlank(message = "产品名称不能为空")
    private String productName;

    /**
     * 产品型号
     */
    private String productModel;

    /**
     * 数量
     */
    private Integer quantity;

    /**
     * 性能说明
     */
    private String performanceDesc;

    /**
     * 投入使用开始时间
     */
    private Date useStartDate;

    /**
     * 投入使用结束时间
     */
    private Date useEndDate;

    /**
     * 是否有购买合同（0否 1是）
     */
    private String hasPurchaseContract;

    /**
     * 产品分类
     */
    private String productCategory;

    /**
     * 实物图片
     */
    private String productImage;

    /**
     * 相关图片
     */
    private String relatedImages;

    /**
     * 备注
     */
    private String remark;

}
