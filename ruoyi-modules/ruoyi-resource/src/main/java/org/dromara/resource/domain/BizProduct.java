package org.dromara.resource.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.util.Date;

/**
 * 产品信息对象 biz_product
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_product")
public class BizProduct extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 关联部门ID
     */
    private Long deptId;

    /**
     * 产品名称
     */
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
