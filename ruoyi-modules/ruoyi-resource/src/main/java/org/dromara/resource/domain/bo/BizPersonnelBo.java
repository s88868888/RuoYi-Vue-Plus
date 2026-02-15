package org.dromara.resource.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.resource.domain.BizPersonnel;

import java.io.Serial;
import java.util.Date;

/**
 * 人员信息业务对象 biz_personnel
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BizPersonnel.class, reverseConvertGenerate = false)
public class BizPersonnelBo extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "关联部门ID")
    private Long deptId;

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "性别（0男 1女）")
    private String gender;

    @Schema(description = "出生日期")
    private Date birthDate;

    @Schema(description = "证件类型")
    private String idCardType;

    @Schema(description = "证件号码")
    private String idCardNumber;

    @Schema(description = "联系方式")
    private String phone;

    @Schema(description = "职务")
    private String position;

    @Schema(description = "入职时间")
    private Date hireDate;

    @Schema(description = "工作年限")
    private Integer workYears;

    @Schema(description = "状态（0在职 1离职）")
    private String status;

    @Schema(description = "照片")
    private String photo;

    @Schema(description = "身份证正面")
    private String idCardFront;

    @Schema(description = "身份证反面")
    private String idCardBack;

    @Schema(description = "社保材料")
    private String socialSecurity;

    @Schema(description = "简历附件")
    private String resumeFile;

    @Schema(description = "备注")
    private String remark;

}
