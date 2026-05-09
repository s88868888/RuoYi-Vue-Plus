package org.dromara.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.review.domain.ReviewStandardRule;
import org.dromara.review.domain.bo.ReviewStandardRuleBo;
import org.dromara.review.domain.vo.ReviewStandardRuleVo;
import org.dromara.review.mapper.ReviewStandardRuleMapper;
import org.dromara.review.service.IReviewStandardRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * 审核标准规则Service业务层处理
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ReviewStandardRuleServiceImpl implements IReviewStandardRuleService {

    private final ReviewStandardRuleMapper baseMapper;

    /**
     * 查询审核标准规则
     */
    @Override
    public ReviewStandardRuleVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 按标准ID查询规则列表
     */
    @Override
    public List<ReviewStandardRuleVo> queryListByStandardId(Long standardId) {
        LambdaQueryWrapper<ReviewStandardRule> lqw = Wrappers.lambdaQuery();
        lqw.eq(ReviewStandardRule::getStandardId, standardId);
        lqw.orderByAsc(ReviewStandardRule::getSortOrder);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<ReviewStandardRule> buildQueryWrapper(ReviewStandardRuleBo bo) {
        LambdaQueryWrapper<ReviewStandardRule> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getStandardId() != null, ReviewStandardRule::getStandardId, bo.getStandardId());
        lqw.like(StringUtils.isNotBlank(bo.getContent()), ReviewStandardRule::getContent, bo.getContent());
        lqw.eq(StringUtils.isNotBlank(bo.getSeverity()), ReviewStandardRule::getSeverity, bo.getSeverity());
        lqw.eq(StringUtils.isNotBlank(bo.getCategory()), ReviewStandardRule::getCategory, bo.getCategory());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), ReviewStandardRule::getStatus, bo.getStatus());
        lqw.orderByAsc(ReviewStandardRule::getSortOrder);
        return lqw;
    }

    /**
     * 新增审核标准规则
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(ReviewStandardRuleBo bo) {
        ReviewStandardRule add = MapstructUtils.convert(bo, ReviewStandardRule.class);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改审核标准规则
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(ReviewStandardRuleBo bo) {
        ReviewStandardRule update = MapstructUtils.convert(bo, ReviewStandardRule.class);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 校验并批量删除审核标准规则信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        return baseMapper.deleteByIds(ids) > 0;
    }

}
