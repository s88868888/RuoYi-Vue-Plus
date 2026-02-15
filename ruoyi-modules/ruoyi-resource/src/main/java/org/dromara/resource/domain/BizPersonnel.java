package org.dromara.resource.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.util.Date;

/**
 * 人员信息对象 biz_personnel
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_personnel")
public class BizPersonnel extends TenantEntity {

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
     * 姓名
     */
    private String name;

    /**
     * 性别（0男 1女）
     */
    private String gender;

    /**
     * 出生日期
     */
    private Date birthDate;

    /**
     * 证件类型
     */
    private String idCardType;

    /**
     * 证件号码
     */
    private String idCardNumber;

    /**
     * 联系方式
     */
    private String phone;

    /**
     * 职务
     */
    private String position;

    /**
     * 入职时间
     */
    private Date hireDate;

    /**
     * 工作年限
     */
    private Integer workYears;

    /**
     * 状态（0在职 1离职）
     */
    private String status;

    /**
     * 照片
     */
    private String photo;

    /**
     * 身份证正面
     */
    private String idCardFront;

    /**
     * 身份证反面
     */
    private String idCardBack;

    /**
     * 社保材料
     */
    private String socialSecurity;

    /**
     * 简历附件
     */
    private String resumeFile;

    /**
     * 备注
     */
    private String remark;

}
