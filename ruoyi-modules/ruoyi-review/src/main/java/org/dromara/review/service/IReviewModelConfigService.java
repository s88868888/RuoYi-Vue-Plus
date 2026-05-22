package org.dromara.review.service;

import org.dromara.common.ai.dto.AiModelConfigDto;
import org.dromara.review.domain.bo.ReviewModelConfigBo;
import org.dromara.review.domain.vo.ReviewModelConfigTestVo;
import org.dromara.review.domain.vo.ReviewModelConfigVo;

import java.util.Collection;
import java.util.List;

/**
 * AI 模型配置 Service 接口
 *
 * @author Linson
 * @date 2026-05-22
 */
public interface IReviewModelConfigService {

    /** 查询单条 */
    ReviewModelConfigVo queryById(Long id);

    /** 列表查询，可按 provider / enabled / purpose 过滤 */
    List<ReviewModelConfigVo> queryList(String provider, String enabled, String purpose);

    Boolean insertByBo(ReviewModelConfigBo bo);

    Boolean updateByBo(ReviewModelConfigBo bo);

    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    /**
     * 取出转好的 DTO，喂给 AiChatService。
     * 同时校验 enabled='1'，禁用直接抛异常，避免审核走到一半才发现配置失效。
     */
    AiModelConfigDto getEnabledDto(Long id);

    /**
     * 取指定用途下的默认/启用配置，找不到返回 null。
     * 选取规则：purpose 匹配 + enabled=1，按 is_default DESC, id ASC 取第一条。
     * <p>
     * 用于 OCR 链路：OcrProviderFactory 启动/每次调用前查 purpose=ocr 的默认配置。
     */
    AiModelConfigDto getDefaultByPurpose(String purpose);

    /**
     * 测试连接（不落库）。
     * - chat 通道：发一句最简短 prompt（"ping"），看模型是否返回内容
     * - ocr 通道（paddleocr）：GET /health 或最小 1x1 PNG 跑一次
     * - ocr 通道（qwen-vl-ocr）：用最小图片走 chatWithImage
     * 接受 Bo 而非 id：让运营在保存前就能验证配置是否正确。
     */
    ReviewModelConfigTestVo testConnection(ReviewModelConfigBo bo);
}
