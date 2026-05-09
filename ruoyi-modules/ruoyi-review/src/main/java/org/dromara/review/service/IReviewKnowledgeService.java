package org.dromara.review.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.review.domain.ReviewKnowledgeCase;
import org.dromara.review.domain.ReviewKnowledgeMisjudgment;
import org.dromara.review.domain.ReviewKnowledgePattern;
import org.dromara.review.domain.bo.ReviewKnowledgeBo;
import org.dromara.review.domain.vo.ReviewKnowledgeVo;
import org.dromara.review.domain.vo.ReviewStandardVo;

import java.util.Collection;
import java.util.List;

/**
 * 审核知识库Service接口
 *
 * @author ruoyi
 * @date 2026-05-09
 */
public interface IReviewKnowledgeService {

    /**
     * 查询审核知识库
     */
    ReviewKnowledgeVo queryById(Long id);

    /**
     * 查询审核知识库分页列表
     */
    TableDataInfo<ReviewKnowledgeVo> queryPageList(ReviewKnowledgeBo bo, PageQuery pageQuery);

    /**
     * 查询审核知识库列表
     */
    List<ReviewKnowledgeVo> queryList(ReviewKnowledgeBo bo);

    /**
     * 新增审核知识库
     */
    Boolean insertByBo(ReviewKnowledgeBo bo);

    /**
     * 修改审核知识库
     */
    Boolean updateByBo(ReviewKnowledgeBo bo);

    /**
     * 校验并批量删除审核知识库信息
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    /**
     * 查询知识库关联的标准列表
     */
    List<ReviewStandardVo> queryLinkedStandards(Long knowledgeId);

    /**
     * 查询知识库下的案例列表
     */
    List<ReviewKnowledgeCase> queryCases(Long knowledgeId);

    /**
     * 查询知识库下的问题模式列表
     */
    List<ReviewKnowledgePattern> queryPatterns(Long knowledgeId);

    /**
     * 查询知识库下的误判记录列表
     */
    List<ReviewKnowledgeMisjudgment> queryMisjudgments(Long knowledgeId);

}
