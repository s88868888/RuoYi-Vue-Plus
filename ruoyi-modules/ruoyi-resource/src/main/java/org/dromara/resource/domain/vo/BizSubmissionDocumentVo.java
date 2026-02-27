package org.dromara.resource.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import org.dromara.resource.domain.BizSubmissionDocument;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 标书文档视图对象 biz_submission_document
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BizSubmissionDocument.class)
public class BizSubmissionDocumentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @ExcelProperty(value = "主键ID")
    private Long id;

    /**
     * 投标项目ID
     */
    @ExcelProperty(value = "投标项目ID")
    private Long bidSubmissionId;

    /**
     * 公司ID
     */
    @ExcelProperty(value = "公司ID")
    private Long companyId;

    /**
     * 公司名称
     */
    @ExcelProperty(value = "公司名称")
    private String companyName;

    /**
     * 文档名称
     */
    @ExcelProperty(value = "文档名称")
    private String documentName;

    /**
     * 文档类型
     */
    @ExcelProperty(value = "文档类型", converter = ExcelDictConvert  .class)
    @ExcelDictFormat(dictType = "submission_document_type")
    private String documentType;

    /**
     * 同类型文档序号
     */
    @ExcelProperty(value = "文档序号")
    private Integer documentNo;

    /**
     * 文档内容（Markdown格式）
     */
    private String documentContent;

    /**
     * 文档HTML内容
     */
    private String documentHtml;

    /**
     * 文档文件路径
     */
    private String filePath;

    /**
     * 文件大小（字节）
     */
    @ExcelProperty(value = "文件大小")
    private Long fileSize;

    /**
     * 生成状态
     */
    @ExcelProperty(value = "生成状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "document_generation_status")
    private String generationStatus;

    /**
     * 生成进度（0-100）
     */
    @ExcelProperty(value = "生成进度")
    private Integer generationProgress;

    /**
     * 开始生成时间
     */
    @ExcelProperty(value = "开始生成时间")
    private Date generationStartTime;

    /**
     * 完成时间
     */
    @ExcelProperty(value = "完成时间")
    private Date generationEndTime;

    /**
     * 生成耗时（秒）
     */
    @ExcelProperty(value = "生成耗时")
    private Integer generationDuration;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 使用的AI模型
     */
    @ExcelProperty(value = "AI模型")
    private String aiModel;

    /**
     * 消耗的Token数
     */
    @ExcelProperty(value = "Token消耗")
    private Integer aiTokensUsed;

    /**
     * 文档版本号
     */
    @ExcelProperty(value = "版本号")
    private Integer version;

    /**
     * 是否最新版本
     */
    @ExcelProperty(value = "是否最新版本")
    private String isLatest;

    /**
     * 备注
     */
    @ExcelProperty(value = "备注")
    private String remark;

    /**
     * 创建时间
     */
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
