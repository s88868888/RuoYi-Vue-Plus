package org.dromara.resource.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.dromara.resource.domain.BizPersonnel;

import java.io.Serial;
import java.util.Date;

/**
 * 人员信息视图对象 biz_personnel
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Data
@AutoMapper(target = BizPersonnel.class)
public class BizPersonnelVo {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "关联部门ID")
    private Long deptId;

    @Schema(description = "部门名称")
    private String deptName;

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

    @Schema(description = "创建时间")
    private Date createTime;

}
