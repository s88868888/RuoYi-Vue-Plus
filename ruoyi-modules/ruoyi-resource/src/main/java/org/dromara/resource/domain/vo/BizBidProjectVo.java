package org.dromara.resource.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.dromara.resource.domain.BizBidProject;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 招标项目视图对象
 *
 * @author ruoyi
 * @date 2026-02-23
 */
@Data
@AutoMapper(target = BizBidProject.class)
public class BizBidProjectVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "关联部门ID")
    private Long deptId;

    @Schema(description = "项目名称")
    private String projectName;

    @Schema(description = "招标单位")
    private String bidOrg;

    @Schema(description = "项目类型")
    private String projectType;

    @Schema(description = "预算金额（万元）")
    private BigDecimal budgetAmount;

    @Schema(description = "发布日期")
    private Date publishDate;

    @Schema(description = "截止日期")
    private Date deadline;

    @Schema(description = "契合度（0-100）")
    private Integer matchDegree;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "项目地区")
    private String projectRegion;

    @Schema(description = "招标方式")
    private String bidMethod;

    @Schema(description = "联系人")
    private String contactPerson;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "项目来源")
    private String projectSource;

    @Schema(description = "来源链接")
    private String sourceUrl;

    @Schema(description = "项目描述")
    private String projectDesc;

    @Schema(description = "附件URL，多个用逗号分隔")
    private String attachments;

    @Schema(description = "附件名称，多个用逗号分隔")
    private String attachmentName;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "AI分析提示词")
    private String aiPrompt;

    @Schema(description = "AI分析结果")
    private String aiAnalysisResult;

    @Schema(description = "AI分析状态")
    private String aiAnalysisStatus;

    @Schema(description = "创建人")
    private Long createBy;

    @Schema(description = "创建时间")
    private Date createTime;

}
