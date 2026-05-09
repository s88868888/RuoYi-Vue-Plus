package org.dromara.review.service;

import org.dromara.review.domain.bo.ReviewPromptTemplateBo;
import org.dromara.review.domain.vo.ReviewPromptTemplateVo;

import java.util.Collection;
import java.util.List;

/**
 * AI提示词模板Service接口
 *
 * @author ruoyi
 * @date 2026-05-09
 */
public interface IReviewPromptTemplateService {

    /**
     * 查询AI提示词模板
     */
    ReviewPromptTemplateVo queryById(Long id);

    /**
     * 按类型查询AI提示词模板列表
     */
    List<ReviewPromptTemplateVo> queryListByType(String type);

    /**
     * 新增AI提示词模板
     */
    Boolean insertByBo(ReviewPromptTemplateBo bo);

    /**
     * 修改AI提示词模板
     */
    Boolean updateByBo(ReviewPromptTemplateBo bo);

    /**
     * 校验并批量删除AI提示词模板信息
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

}
