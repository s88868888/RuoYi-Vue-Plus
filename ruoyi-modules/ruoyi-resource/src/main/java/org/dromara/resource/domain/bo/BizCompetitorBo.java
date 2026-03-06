package org.dromara.resource.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.resource.domain.BizCompetitor;

/**
 * 竞争公司业务对象 biz_competitor
 *
 * @author ruoyi
 * @date 2026-03-06
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BizCompetitor.class, reverseConvertGenerate = false)
public class BizCompetitorBo extends BaseEntity {

    @NotNull(message = "主键ID不能为空", groups = {EditGroup.class})
    private Long id;

    private Long deptId;

    @NotBlank(message = "竞争公司名称不能为空", groups = {AddGroup.class, EditGroup.class})
    private String companyName;

    private String companyType;
    private String businessScope;
    private String registeredCapital;
    private String foundedYear;
    private String province;
    private String city;
    private String website;
    private String contactPerson;
    private String contactPhone;
    private String contactEmail;
    private String strengths;
    private String weaknesses;
    private String mainProducts;
    private String competitorLevel;
    private String attachmentUrl;
    private String attachmentName;
    private String dataPermissionType;
    private String remark;

}
