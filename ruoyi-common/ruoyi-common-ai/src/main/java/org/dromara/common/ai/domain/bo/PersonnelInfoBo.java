package org.dromara.common.ai.domain.bo;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 人员信息业务对象
 *
 * @author ruoyi
 */
@Data
public class PersonnelInfoBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 姓名
     */
    @NotBlank(message = "姓名不能为空")
    private String name;

    /**
     * 职位
     */
    private String position;

    /**
     * 部门
     */
    private String department;

    /**
     * 学历
     */
    private String education;

    /**
     * 专业
     */
    private String major;

    /**
     * 技能
     */
    private String skills;

    /**
     * 工作经验
     */
    private String workExperience;

    /**
     * 个人简介
     */
    private String profile;

}
