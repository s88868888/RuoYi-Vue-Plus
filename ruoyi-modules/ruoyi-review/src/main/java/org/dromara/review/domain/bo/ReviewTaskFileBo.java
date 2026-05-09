package org.dromara.review.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.review.domain.ReviewTaskFile;

/**
 * 审核任务附件业务对象 review_task_file
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ReviewTaskFile.class, reverseConvertGenerate = false)
public class ReviewTaskFileBo extends BaseEntity {

    /**
     * 主键ID
     */
    @NotNull(message = "主键不能为空", groups = {EditGroup.class})
    private Long id;

    /**
     * 任务ID
     */
    @NotNull(message = "任务ID不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long taskId;

    /**
     * 文件名称
     */
    @NotBlank(message = "文件名称不能为空", groups = {AddGroup.class, EditGroup.class})
    private String fileName;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 排序顺序
     */
    private Integer sortOrder;

}
