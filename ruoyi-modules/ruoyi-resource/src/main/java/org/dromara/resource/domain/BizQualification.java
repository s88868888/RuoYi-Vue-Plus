package org.dromara.resource.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.util.Date;

/**
 * 企业资质对象 biz_qualification
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_qualification")
public class BizQualification extends TenantEntity {

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
     * 证书编号
     */
    private String certNumber;

    /**
     * 证书名称
     */
    private String certName;

    /**
     * 证书类别
     */
    private String certCategory;

    /**
     * 证书状态
     */
    private String certStatus;

    /**
     * 发证机关
     */
    private String issuingAuthority;

    /**
     * 有效期开始时间
     */
    private Date validStartDate;

    /**
     * 有效期结束时间
     */
    private Date validEndDate;

    /**
     * 证书图片
     */
    private String certImages;

    /**
     * 数据权限类型（0公域 1私域）
     */
    private String dataPermissionType;

    /**
     * 备注
     */
    private String remark;

}
