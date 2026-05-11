package org.dromara.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import cn.hutool.core.collection.CollUtil;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.review.domain.ReviewKnowledge;
import org.dromara.review.domain.ReviewKnowledgeCase;
import org.dromara.review.domain.ReviewKnowledgePattern;
import org.dromara.review.domain.ReviewStandard;
import org.dromara.review.domain.ReviewStandardKnowledge;
import org.dromara.review.domain.ReviewStandardRule;
import org.dromara.review.domain.bo.ReviewStandardBo;
import org.dromara.review.domain.vo.ReviewKnowledgeVo;
import org.dromara.review.domain.vo.ReviewStandardVo;
import org.dromara.review.mapper.ReviewKnowledgeCaseMapper;
import org.dromara.review.mapper.ReviewKnowledgeMapper;
import org.dromara.review.mapper.ReviewKnowledgePatternMapper;
import org.dromara.review.mapper.ReviewStandardKnowledgeMapper;
import org.dromara.review.mapper.ReviewStandardMapper;
import org.dromara.review.mapper.ReviewStandardRuleMapper;
import org.dromara.review.service.IReviewStandardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 审核标准Service业务层处理
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ReviewStandardServiceImpl implements IReviewStandardService {

    private final ReviewStandardMapper baseMapper;
    private final ReviewStandardRuleMapper reviewStandardRuleMapper;
    private final ReviewStandardKnowledgeMapper reviewStandardKnowledgeMapper;
    private final ReviewKnowledgeMapper reviewKnowledgeMapper;
    private final ReviewKnowledgeCaseMapper reviewKnowledgeCaseMapper;
    private final ReviewKnowledgePatternMapper reviewKnowledgePatternMapper;

    /**
     * 查询审核标准详情（同时查关联的知识库和规则列表）
     */
    @Override
    public ReviewStandardVo queryById(Long id) {
        ReviewStandardVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            // 查询关联的规则列表
            List<ReviewStandardRule> rules = reviewStandardRuleMapper.selectList(
                Wrappers.<ReviewStandardRule>lambdaQuery()
                    .eq(ReviewStandardRule::getStandardId, id)
                    .orderByAsc(ReviewStandardRule::getSortOrder)
            );
            log.debug("标准[{}]关联规则数: {}", id, rules.size());

            // 查询关联的知识库列表
            List<ReviewStandardKnowledge> knowledgeList = reviewStandardKnowledgeMapper.selectList(
                Wrappers.<ReviewStandardKnowledge>lambdaQuery()
                    .eq(ReviewStandardKnowledge::getStandardId, id)
            );
            log.debug("标准[{}]关联知识库数: {}", id, knowledgeList.size());
        }
        return vo;
    }

    /**
     * 查询审核标准分页列表
     */
    @Override
    public TableDataInfo<ReviewStandardVo> queryPageList(ReviewStandardBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ReviewStandard> lqw = buildQueryWrapper(bo);
        Page<ReviewStandardVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    private LambdaQueryWrapper<ReviewStandard> buildQueryWrapper(ReviewStandardBo bo) {
        LambdaQueryWrapper<ReviewStandard> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getName()), ReviewStandard::getName, bo.getName());
        lqw.eq(StringUtils.isNotBlank(bo.getType()), ReviewStandard::getType, bo.getType());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), ReviewStandard::getStatus, bo.getStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getIsSystem()), ReviewStandard::getIsSystem, bo.getIsSystem());
        lqw.orderByDesc(ReviewStandard::getCreateTime);
        return lqw;
    }

    /**
     * 新增审核标准
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(ReviewStandardBo bo) {

        bo.setCreateBy(LoginHelper.getUserId());
        bo.setCreateTime(new Date());
        bo.setUpdateBy(LoginHelper.getUserId());
        bo.setUpdateTime(new Date());
        ReviewStandard add = MapstructUtils.convert(bo, ReviewStandard.class);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改审核标准
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(ReviewStandardBo bo) {

        bo.setUpdateBy(LoginHelper.getUserId());
        bo.setUpdateTime(new Date());
        ReviewStandard update = MapstructUtils.convert(bo, ReviewStandard.class);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 校验并批量删除审核标准信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        return baseMapper.deleteByIds(ids) > 0;
    }

    @Override
    public List<ReviewKnowledgeVo> queryLinkedKnowledges(Long standardId) {
        List<ReviewStandardKnowledge> skList = reviewStandardKnowledgeMapper.selectList(
            Wrappers.<ReviewStandardKnowledge>lambdaQuery()
                .eq(ReviewStandardKnowledge::getStandardId, standardId)
        );
        if (CollUtil.isEmpty(skList)) {
            return Collections.emptyList();
        }
        List<Long> knowledgeIds = skList.stream()
            .map(ReviewStandardKnowledge::getKnowledgeId)
            .collect(Collectors.toList());
        List<ReviewKnowledgeVo> voList = reviewKnowledgeMapper.selectVoByIds(knowledgeIds);

        // 填充案例数和模式数
        if (CollUtil.isNotEmpty(voList)) {
            List<ReviewKnowledgeCase> allCases = reviewKnowledgeCaseMapper.selectList(
                Wrappers.<ReviewKnowledgeCase>lambdaQuery()
                    .in(ReviewKnowledgeCase::getKnowledgeId, knowledgeIds)
                    .select(ReviewKnowledgeCase::getKnowledgeId)
            );
            java.util.Map<Long, Long> caseCountMap = allCases.stream()
                .collect(Collectors.groupingBy(ReviewKnowledgeCase::getKnowledgeId, Collectors.counting()));

            List<ReviewKnowledgePattern> allPatterns = reviewKnowledgePatternMapper.selectList(
                Wrappers.<ReviewKnowledgePattern>lambdaQuery()
                    .in(ReviewKnowledgePattern::getKnowledgeId, knowledgeIds)
                    .select(ReviewKnowledgePattern::getKnowledgeId)
            );
            java.util.Map<Long, Long> patternCountMap = allPatterns.stream()
                .collect(Collectors.groupingBy(ReviewKnowledgePattern::getKnowledgeId, Collectors.counting()));

            for (ReviewKnowledgeVo vo : voList) {
                vo.setCaseCount(caseCountMap.getOrDefault(vo.getId(), 0L).intValue());
                vo.setPatternCount(patternCountMap.getOrDefault(vo.getId(), 0L).intValue());
            }
        }
        return voList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void linkKnowledge(Long standardId, Long knowledgeId) {
        Long count = reviewStandardKnowledgeMapper.selectCount(
            Wrappers.<ReviewStandardKnowledge>lambdaQuery()
                .eq(ReviewStandardKnowledge::getStandardId, standardId)
                .eq(ReviewStandardKnowledge::getKnowledgeId, knowledgeId)
        );
        if (count > 0) {
            return;
        }
        ReviewStandardKnowledge sk = new ReviewStandardKnowledge();
        sk.setStandardId(standardId);
        sk.setKnowledgeId(knowledgeId);
        sk.setSyncStatus("0");
        reviewStandardKnowledgeMapper.insert(sk);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlinkKnowledge(Long standardId, Long knowledgeId) {
        reviewStandardKnowledgeMapper.delete(
            Wrappers.<ReviewStandardKnowledge>lambdaQuery()
                .eq(ReviewStandardKnowledge::getStandardId, standardId)
                .eq(ReviewStandardKnowledge::getKnowledgeId, knowledgeId)
        );
    }

}
