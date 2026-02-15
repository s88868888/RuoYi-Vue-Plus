package org.dromara.resource.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.resource.domain.BizPatentMedal;

import java.util.Date;

/**
 * 专利奖章业务对象 biz_patent_medal
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BizPatentMedal.class, reverseConvertGenerate = false)
public class BizPatentMedalBo extends BaseEntity {

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
     * 专利名称
     */
    @NotBlank(message = "专利名称不能为空", groups = {AddGroup.class, EditGroup.class})
    private String patentName;

    /**
     * 专利类型（1发明专利 2实用新型专利 3外观设计专利）
     */
    @NotBlank(message = "专利类型不能为空", groups = {AddGroup.class, EditGroup.class})
    private String patentType;

    /**
     * 专利号
     */
    private String patentNumber;

    /**
     * 授权公告日
     */
    private Date authorizationDate;

    /**
     * 专利权人
     */
    private String patentee;

    /**
     * 设计人/发明人
     */
    private String inventor;

    /**
     * 所属领域
     */
    private String field;

    /**
     * 专利摘要
     */
    private String patentAbstract;

    /**
     * 专利图片
     */
    private String patentImage;

    /**
     * 证书图片
     */
    private String certificateImage;

    /**
     * 状态（0有效 1无效）
     */
    private String status;

    /**
     * 数据权限类型（0公域 1私域）
     */
    private String dataPermissionType;

    /**
     * 备注
     */
    private String remark;

}
