package org.dromara.review.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.review.domain.bo.ReviewStandardRuleBo;
import org.dromara.review.domain.vo.ReviewStandardRuleVo;

import java.util.Collection;
import java.util.List;

/**
 * 审核标准规则Service接口
 *
 * @author ruoyi
 * @date 2026-05-09
 */
public interface IReviewStandardRuleService {

    /**
     * 查询审核标准规则
     */
    ReviewStandardRuleVo queryById(Long id);

    /**
     * 按标准ID查询规则列表
     */
    List<ReviewStandardRuleVo> queryListByStandardId(Long standardId);

    /**
     * 分页查询规则列表
     */
    TableDataInfo<ReviewStandardRuleVo> queryPageList(ReviewStandardRuleBo bo, PageQuery pageQuery);

    /**
     * 新增审核标准规则
     */
    Boolean insertByBo(ReviewStandardRuleBo bo);

    /**
     * 修改审核标准规则
     */
    Boolean updateByBo(ReviewStandardRuleBo bo);

    /**
     * 校验并批量删除审核标准规则信息
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

}
