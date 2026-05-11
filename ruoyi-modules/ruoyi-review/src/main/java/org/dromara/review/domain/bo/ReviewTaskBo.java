package org.dromara.review.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.review.domain.ReviewTask;

import java.util.List;

/**
 * 审核任务业务对象 review_task
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ReviewTask.class, reverseConvertGenerate = false)
public class ReviewTaskBo extends BaseEntity {

    /**
     * 主键ID
     */
    @NotNull(message = "主键不能为空", groups = {EditGroup.class})
    private Long id;

    /**
     * 任务名称
     */
    @NotBlank(message = "任务名称不能为空", groups = {AddGroup.class, EditGroup.class})
    private String taskName;

    /**
     * 任务类型
     */
    @NotBlank(message = "任务类型不能为空", groups = {AddGroup.class, EditGroup.class})
    private String taskType;

    /**
     * 来源ID
     */
    private Long sourceId;

    /**
     * 来源类型
     */
    private String sourceType;

    /**
     * 任务状态（查询条件）
     */
    private String status;

    /**
     * 通过状态（查询条件）
     */
    private String passStatus;

    /**
     * 关联的标准ID列表（非Entity字段，Service层处理）
     */
    private List<Long> standardIds;

    /**
     * 表单快照
     */
    private String formSnapshot;

    /**
     * 附件列表（非Entity字段，Service层处理）
     */
    private List<TaskFileBo> files;

    /**
     * 备注
     */
    private String remark;

    @Data
    public static class TaskFileBo {
        private Long ossId;
        private String fileName;
        private String fileType;
        private String filePath;
        private Long fileSize;
    }
}
