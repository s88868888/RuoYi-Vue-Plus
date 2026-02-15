package org.dromara.resource.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.resource.domain.BizQualification;

import java.util.Date;

/**
 * 企业资质业务对象 biz_qualification
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BizQualification.class, reverseConvertGenerate = false)
public class BizQualificationBo extends BaseEntity {

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
     * 证书编号
     */
    @NotBlank(message = "证书编号不能为空", groups = {AddGroup.class, EditGroup.class})
    private String certNumber;

    /**
     * 证书名称
     */
    @NotBlank(message = "证书名称不能为空", groups = {AddGroup.class, EditGroup.class})
    private String certName;

    /**
     * 证书类别
     */
    @NotBlank(message = "证书类别不能为空", groups = {AddGroup.class, EditGroup.class})
    private String certCategory;

    /**
     * 证书状态
     */
    private String certStatus;

    /**
     * 发证机关
     */
    @NotBlank(message = "发证机关不能为空", groups = {AddGroup.class, EditGroup.class})
    private String issuingAuthority;

    /**
     * 有效期开始时间
     */
    @NotNull(message = "有效期开始时间不能为空", groups = {AddGroup.class, EditGroup.class})
    private Date validStartDate;

    /**
     * 有效期结束时间
     */
    private Date validEndDate;

    /**
     * 证书图片
     */
    @NotBlank(message = "证书图片不能为空", groups = {AddGroup.class, EditGroup.class})
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
