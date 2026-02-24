package org.dromara.resource.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.resource.domain.BizBidProject;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 招标项目业务对象
 *
 * @author ruoyi
 * @date 2026-02-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BizBidProject.class, reverseConvertGenerate = false)
public class BizBidProjectBo extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 关联部门ID
     */
    private Long deptId;

    /**
     * 项目名称
     */
    @NotBlank(message = "项目名称不能为空")
    private String projectName;

    /**
     * 招标单位
     */
    private String bidOrg;

    /**
     * 项目类型（engineering工程 goods货物 service服务）
     */
    private String projectType;

    /**
     * 预算金额（万元）
     */
    private BigDecimal budgetAmount;

    /**
     * 发布日期
     */
    private Date publishDate;

    /**
     * 截止日期
     */
    private Date deadline;

    /**
     * 契合度（0-100）
     */
    private Integer matchDegree;

    /**
     * 状态（following跟进中 bid已投标 won已中标 lost未中标 abandoned已放弃）
     */
    private String status;

    /**
     * 项目地区
     */
    private String projectRegion;

    /**
     * 招标方式（public公开招标 invite邀请招标 competitive竞争性谈判 inquiry询价采购 single单一来源）
     */
    private String bidMethod;

    /**
     * 联系人
     */
    private String contactPerson;

    /**
     * 联系电话
     */
    private String contactPhone;

    /**
     * 项目来源（manual手动录入 crawler爬虫获取）
     */
    private String projectSource;

    /**
     * 来源链接
     */
    private String sourceUrl;

    /**
     * 项目描述
     */
    private String projectDesc;

    /**
     * 附件URL，多个用逗号分隔
     */
    private String attachments;

    /**
     * 附件名称，多个用逗号分隔
     */
    private String attachmentName;

    /**
     * 备注
     */
    private String remark;

    /**
     * AI分析提示词
     */
    private String aiPrompt;

    /**
     * AI分析结果
     */
    private String aiAnalysisResult;

    /**
     * AI分析状态（pending待分析 analyzing分析中 completed已完成 failed失败）
     */
    private String aiAnalysisStatus;

}
