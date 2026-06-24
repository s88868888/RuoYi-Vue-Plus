package org.dromara.review.service;

import org.dromara.review.domain.ReviewStandardRule;
import org.dromara.review.domain.vo.ReviewToolResultVo;

import java.util.List;
import java.util.Map;

/**
 * AI 审核工具（附件对比 / 内容审查）结果聚合服务。
 *
 * @author Linson
 * @date 2026-06-23
 */
public interface IReviewToolService {

    /**
     * 聚合工具审核结果（含文件URL、searchable、OCR状态、问题明细、关注列表/要点）。
     * 顺带懒触发 PDF 附件的 OCR。
     *
     * @param taskId 审核任务ID
     * @return 聚合结果VO；任务不存在返回 null
     */
    ReviewToolResultVo getToolResult(Long taskId);

    /**
     * 保存附件对比差异清单批注（COMPARE）。
     *
     * @param taskId   任务ID
     * @param noteData 批注 JSON（签名→批注内容）
     */
    void saveCompareNote(Long taskId, String noteData);

    /**
     * 保存单条问题批注（按 taskId + fieldName 定位匹配的结果明细）。
     *
     * @param taskId    任务ID
     * @param fieldName 字段名（定位用）
     * @param note      批注内容
     */
    void saveIssueNote(Long taskId, String fieldName, String note);

    /**
     * 保存内容审查脱敏手动框选数据。
     *
     * @param taskId     任务ID
     * @param redactData 手动脱敏框 JSON
     */
    void saveRedactData(Long taskId, String redactData);

    /**
     * 按 ossId 触发某附件的 OCR（查看器自愈用）。
     *
     * @param ossId OSS文件ID
     */
    void triggerOcrByOssId(Long ossId);

    /**
     * 查询某附件的 OCR 状态（查看器轮询用）。状态为空的 PDF 会顺带懒触发一次。
     *
     * @param ossId OSS文件ID
     * @return {ocrStatus, searchableUrl}
     */
    Map<String, Object> getOcrStatus(Long ossId);

    /**
     * 查询某任务实际使用的规则库（任务关联标准下的启用规则，查看器「查看规则」用）。
     *
     * @param taskId 审核任务ID
     * @return 规则列表
     */
    List<ReviewStandardRule> getToolRules(Long taskId);

    /**
     * 把已上传 OSS 的 Word(doc/docx) 文件转成 PDF 并传回 OSS（附件对比基准/对比件为 Word 时，
     * 转 PDF 后查看器才能进双 PDF 模式做字符级 diff）。已是 PDF 则原样返回。
     *
     * @param ossId 源文件 OSS ID
     * @return {ossId, url, name, converted}
     */
    Map<String, Object> convertWordToPdf(Long ossId);
}
