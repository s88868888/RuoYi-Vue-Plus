package org.dromara.resource.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.util.Date;

/**
 * 人员资格证书对象 biz_personnel_certificate
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_personnel_certificate")
public class BizPersonnelCertificate extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 人员ID
     */
    private Long personnelId;

    /**
     * 证书名称
     */
    private String certificateName;

    /**
     * 证书编号
     */
    private String certificateNumber;

    /**
     * 发证日期
     */
    private Date issueDate;

    /**
     * 有效期至
     */
    private Date expiryDate;

    /**
     * 发证机关
     */
    private String issuingAuthority;

    /**
     * 证书等级
     */
    private String certificateLevel;

    /**
     * 专业
     */
    private String major;

    /**
     * 证书图片
     */
    private String certificateImage;

    /**
     * 备注
     */
    private String remark;

}
