package org.dromara.resource.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.dromara.resource.domain.BizPatentMedal;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 专利奖章视图对象 biz_patent_medal
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Data
@AutoMapper(target = BizPatentMedal.class)
public class BizPatentMedalVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "关联部门ID")
    private Long deptId;

    @Schema(description = "专利名称")
    private String patentName;

    @Schema(description = "专利类型")
    private String patentType;

    @Schema(description = "专利号")
    private String patentNumber;

    @Schema(description = "授权公告日")
    private Date authorizationDate;

    @Schema(description = "专利权人")
    private String patentee;

    @Schema(description = "设计人/发明人")
    private String inventor;

    @Schema(description = "所属领域")
    private String field;

    @Schema(description = "专利摘要")
    private String patentAbstract;

    @Schema(description = "专利图片")
    private String patentImage;

    @Schema(description = "证书图片")
    private String certificateImage;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "数据权限类型（0公域 1私域）")
    private String dataPermissionType;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "创建人")
    private String createByName;

}
