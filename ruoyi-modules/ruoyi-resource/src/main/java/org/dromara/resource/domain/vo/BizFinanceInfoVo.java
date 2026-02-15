package org.dromara.resource.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.dromara.resource.domain.BizFinanceInfo;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 财务信息视图对象 biz_finance_info
 *
 * @author ruoyi
 * @date 2026-02-12
 */
@Data
@AutoMapper(target = BizFinanceInfo.class)
public class BizFinanceInfoVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "关联部门ID")
    private Long deptId;

    @Schema(description = "财务信息名称")
    private String financeName;

    @Schema(description = "信息类型")
    private String infoType;

    @Schema(description = "时间")
    private Date financeDate;

    @Schema(description = "数据权限类型（0私密 1公开）")
    private String dataPermissionType;

    @Schema(description = "附件URL")
    private String attachmentUrl;

    @Schema(description = "附件名称")
    private String attachmentName;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "创建人")
    private String createByName;

}
