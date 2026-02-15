package org.dromara.resource.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.util.Date;

/**
 * 财务信息对象 biz_finance_info
 *
 * @author ruoyi
 * @date 2026-02-12
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_finance_info")
public class BizFinanceInfo extends TenantEntity {

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
     * 财务信息名称
     */
    private String financeName;

    /**
     * 信息类型
     */
    private String infoType;

    /**
     * 时间
     */
    private Date financeDate;

    /**
     * 数据权限类型（0私密 1公开）
     */
    private String dataPermissionType;

    /**
     * 附件URL
     */
    private String attachmentUrl;

    /**
     * 附件名称
     */
    private String attachmentName;

    /**
     * 备注
     */
    private String remark;

}
