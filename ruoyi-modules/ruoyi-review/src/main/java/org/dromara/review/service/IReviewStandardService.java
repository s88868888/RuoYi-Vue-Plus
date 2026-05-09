package org.dromara.review.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.review.domain.bo.ReviewStandardBo;
import org.dromara.review.domain.vo.ReviewStandardVo;

import org.dromara.review.domain.vo.ReviewKnowledgeVo;

import java.util.Collection;
import java.util.List;

/**
 * 审核标准Service接口
 *
 * @author ruoyi
 * @date 2026-05-09
 */
public interface IReviewStandardService {

    /**
     * 查询审核标准详情（同时查关联的知识库和规则列表）
     */
    ReviewStandardVo queryById(Long id);

    /**
     * 查询审核标准分页列表
     */
    TableDataInfo<ReviewStandardVo> queryPageList(ReviewStandardBo bo, PageQuery pageQuery);

    /**
     * 新增审核标准
     */
    Boolean insertByBo(ReviewStandardBo bo);

    /**
     * 修改审核标准
     */
    Boolean updateByBo(ReviewStandardBo bo);

    /**
     * 校验并批量删除审核标准信息
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    /**
     * 查询标准关联的知识库列表
     */
    List<ReviewKnowledgeVo> queryLinkedKnowledges(Long standardId);

    /**
     * 关联知识库
     */
    void linkKnowledge(Long standardId, Long knowledgeId);

    /**
     * 解除关联知识库
     */
    void unlinkKnowledge(Long standardId, Long knowledgeId);

}
