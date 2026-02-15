package org.dromara.resource.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 项目知识对象 biz_project_knowledge
 *
 * @author ruoyi
 * @date 2026-02-12
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_project_knowledge")
public class BizProjectKnowledge extends TenantEntity {

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
     * 知识名称
     */
    private String knowledgeName;

    /**
     * 描述
     */
    private String description;

    /**
     * 挂标项目类型
     */
    private String projectType;

    /**
     * 数据权限类型（0私域 1公域）
     */
    private String dataPermissionType;

    /**
     * 附件URL
     */
    private String attachmentUrl;

    /**
     * 附件名称
     */
    private String attachmentName;

    /**
     * 备注
     */
    private String remark;

}
