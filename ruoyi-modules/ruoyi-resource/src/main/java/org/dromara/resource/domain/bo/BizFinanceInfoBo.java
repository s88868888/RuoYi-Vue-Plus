package org.dromara.resource.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.resource.domain.BizFinanceInfo;

import java.util.Date;

/**
 * 财务信息业务对象 biz_finance_info
 *
 * @author ruoyi
 * @date 2026-02-12
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BizFinanceInfo.class, reverseConvertGenerate = false)
public class BizFinanceInfoBo extends BaseEntity {

    /**
     * 主键ID
     */
    @NotNull(message = "主键ID不能为空", groups = {EditGroup.class})
    private Long id;

    /**
     * 关联部门ID
     */
    private Long deptId;

    /**
     * 财务信息名称
     */
    @NotBlank(message = "财务信息名称不能为空", groups = {AddGroup.class, EditGroup.class})
    private String financeName;

    /**
     * 信息类型
     */
    @NotBlank(message = "信息类型不能为空", groups = {AddGroup.class, EditGroup.class})
    private String infoType;

    /**
     * 时间
     */
    @NotNull(message = "时间不能为空", groups = {AddGroup.class, EditGroup.class})
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
