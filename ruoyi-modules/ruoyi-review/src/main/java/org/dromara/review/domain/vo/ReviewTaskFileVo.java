package org.dromara.review.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.review.domain.ReviewTaskFile;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 审核任务附件视图对象 review_task_file
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ReviewTaskFile.class)
public class ReviewTaskFileVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @ExcelProperty(value = "主键ID")
    private Long id;

    /**
     * 任务ID
     */
    @ExcelProperty(value = "任务ID")
    private Long taskId;

    /**
     * 文件名称
     */
    @ExcelProperty(value = "文件名称")
    private String fileName;

    /**
     * 文件类型
     */
    @ExcelProperty(value = "文件类型")
    private String fileType;

    /**
     * 文件路径
     */
    @ExcelProperty(value = "文件路径")
    private String filePath;

    /**
     * 文件大小（字节）
     */
    @ExcelProperty(value = "文件大小")
    private Long fileSize;

    /**
     * 解析状态
     */
    @ExcelProperty(value = "解析状态")
    private String parseStatus;

    /**
     * 提取文本
     */
    private String extractedText;

    /**
     * 排序顺序
     */
    @ExcelProperty(value = "排序顺序")
    private Integer sortOrder;

    /**
     * 创建时间
     */
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
