package org.dromara.review.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.review.domain.ReviewStandard;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 审核标准视图对象 review_standard
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ReviewStandard.class)
public class ReviewStandardVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @ExcelProperty(value = "主键ID")
    private Long id;

    /**
     * 标准名称
     */
    @ExcelProperty(value = "标准名称")
    private String name;

    /**
     * 标准类型
     */
    @ExcelProperty(value = "标准类型")
    private String type;

    /**
     * 版本号
     */
    @ExcelProperty(value = "版本号")
    private String version;

    /**
     * 描述
     */
    @ExcelProperty(value = "描述")
    private String description;

    /**
     * 规则数量
     */
    @ExcelProperty(value = "规则数量")
    private Integer ruleCount;

    /**
     * 使用次数
     */
    @ExcelProperty(value = "使用次数")
    private Integer useCount;

    /**
     * 是否系统内置
     */
    @ExcelProperty(value = "是否系统内置")
    private String isSystem;

    /**
     * 状态
     */
    @ExcelProperty(value = "状态")
    private String status;

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

    /**
     * 更新时间
     */
    @ExcelProperty(value = "更新时间")
    private Date updateTime;

}
