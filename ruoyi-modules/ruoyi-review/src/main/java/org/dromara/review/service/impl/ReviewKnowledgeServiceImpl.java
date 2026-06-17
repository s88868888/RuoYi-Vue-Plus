package org.dromara.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.collection.CollUtil;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.review.domain.ReviewKnowledge;
import org.dromara.review.domain.ReviewKnowledgeCase;
import org.dromara.review.domain.ReviewKnowledgeMisjudgment;
import org.dromara.review.domain.ReviewKnowledgePattern;
import org.dromara.review.domain.ReviewStandardKnowledge;
import org.dromara.review.domain.ReviewStandardRule;
import org.dromara.review.domain.bo.ReviewKnowledgeBo;
import org.dromara.review.domain.vo.ReviewKnowledgeVo;
import org.dromara.review.domain.vo.ReviewStandardVo;
import org.dromara.review.mapper.ReviewKnowledgeCaseMapper;
import org.dromara.review.mapper.ReviewKnowledgeMapper;
import org.dromara.review.mapper.ReviewKnowledgeMisjudgmentMapper;
import org.dromara.review.mapper.ReviewKnowledgePatternMapper;
import org.dromara.review.mapper.ReviewStandardKnowledgeMapper;
import org.dromara.review.mapper.ReviewStandardMapper;
import org.dromara.review.mapper.ReviewStandardRuleMapper;
import org.dromara.review.service.IReviewKnowledgeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 审核知识库Service业务层处理
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ReviewKnowledgeServiceImpl implements IReviewKnowledgeService {

    private final ReviewKnowledgeMapper baseMapper;
    private final ReviewKnowledgeCaseMapper caseMapper;
    private final ReviewKnowledgePatternMapper patternMapper;
    private final ReviewKnowledgeMisjudgmentMapper misjudgmentMapper;
    private final ReviewStandardKnowledgeMapper standardKnowledgeMapper;
    private final ReviewStandardMapper standardMapper;
    private final ReviewStandardRuleMapper standardRuleMapper;

    /**
     * 查询审核知识库
     */
    @Override
    public ReviewKnowledgeVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 查询审核知识库分页列表
     */
    @Override
    public TableDataInfo<ReviewKnowledgeVo> queryPageList(ReviewKnowledgeBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ReviewKnowledge> lqw = buildQueryWrapper(bo);
        Page<ReviewKnowledgeVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        List<ReviewKnowledgeVo> records = result.getRecords();
        if (CollUtil.isNotEmpty(records)) {
            List<Long> knowledgeIds = records.stream().map(ReviewKnowledgeVo::getId).collect(Collectors.toList());
            // 关联标准数
            List<ReviewStandardKnowledge> allLinks = standardKnowledgeMapper.selectList(
                Wrappers.<ReviewStandardKnowledge>lambdaQuery()
                    .in(ReviewStandardKnowledge::getKnowledgeId, knowledgeIds)
            );
            java.util.Map<Long, Long> linkCountMap = allLinks.stream()
                .collect(Collectors.groupingBy(ReviewStandardKnowledge::getKnowledgeId, Collectors.counting()));
            // 实际案例数
            List<ReviewKnowledgeCase> allCases = caseMapper.selectList(
                Wrappers.<ReviewKnowledgeCase>lambdaQuery()
                    .in(ReviewKnowledgeCase::getKnowledgeId, knowledgeIds)
                    .select(ReviewKnowledgeCase::getKnowledgeId)
            );
            java.util.Map<Long, Long> caseCountMap = allCases.stream()
                .collect(Collectors.groupingBy(ReviewKnowledgeCase::getKnowledgeId, Collectors.counting()));
            // 实际模式数
            List<ReviewKnowledgePattern> allPatterns = patternMapper.selectList(
                Wrappers.<ReviewKnowledgePattern>lambdaQuery()
                    .in(ReviewKnowledgePattern::getKnowledgeId, knowledgeIds)
                    .select(ReviewKnowledgePattern::getKnowledgeId)
            );
            java.util.Map<Long, Long> patternCountMap = allPatterns.stream()
                .collect(Collectors.groupingBy(ReviewKnowledgePattern::getKnowledgeId, Collectors.counting()));

            for (ReviewKnowledgeVo vo : records) {
                vo.setLinkedStandardCount(linkCountMap.getOrDefault(vo.getId(), 0L).intValue());
                vo.setCaseCount(caseCountMap.getOrDefault(vo.getId(), 0L).intValue());
                vo.setPatternCount(patternCountMap.getOrDefault(vo.getId(), 0L).intValue());
            }
        }
        return TableDataInfo.build(result);
    }

    /**
     * 查询审核知识库列表
     */
    @Override
    public List<ReviewKnowledgeVo> queryList(ReviewKnowledgeBo bo) {
        LambdaQueryWrapper<ReviewKnowledge> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<ReviewKnowledge> buildQueryWrapper(ReviewKnowledgeBo bo) {
        LambdaQueryWrapper<ReviewKnowledge> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getName()), ReviewKnowledge::getName, bo.getName());
        lqw.eq(StringUtils.isNotBlank(bo.getType()), ReviewKnowledge::getType, bo.getType());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), ReviewKnowledge::getStatus, bo.getStatus());
        lqw.orderByDesc(ReviewKnowledge::getCreateTime);
        return lqw;
    }

    /**
     * 新增审核知识库
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(ReviewKnowledgeBo bo) {
        ReviewKnowledge add = MapstructUtils.convert(bo, ReviewKnowledge.class);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改审核知识库
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(ReviewKnowledgeBo bo) {
        ReviewKnowledge update = MapstructUtils.convert(bo, ReviewKnowledge.class);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 校验并批量删除审核知识库信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        return baseMapper.deleteByIds(ids) > 0;
    }

    @Override
    public List<ReviewStandardVo> queryLinkedStandards(Long knowledgeId) {
        List<ReviewStandardKnowledge> skList = standardKnowledgeMapper.selectList(
            Wrappers.<ReviewStandardKnowledge>lambdaQuery()
                .eq(ReviewStandardKnowledge::getKnowledgeId, knowledgeId)
        );
        if (CollUtil.isEmpty(skList)) {
            return Collections.emptyList();
        }
        List<Long> standardIds = skList.stream()
            .map(ReviewStandardKnowledge::getStandardId)
            .collect(Collectors.toList());
        List<ReviewStandardVo> standards = standardMapper.selectVoByIds(standardIds);
        // rule_count 字段在表中未维护（恒为0），与标准列表/详情接口口径保持一致，动态统计规则数
        java.util.Map<Long, Long> countMap = standardRuleMapper.selectList(
                Wrappers.<ReviewStandardRule>lambdaQuery()
                    .select(ReviewStandardRule::getStandardId)
                    .in(ReviewStandardRule::getStandardId, standardIds)
            ).stream()
            .collect(Collectors.groupingBy(ReviewStandardRule::getStandardId, Collectors.counting()));
        for (ReviewStandardVo vo : standards) {
            vo.setRuleCount(countMap.getOrDefault(vo.getId(), 0L).intValue());
        }
        return standards;
    }

    @Override
    public List<ReviewKnowledgeCase> queryCases(Long knowledgeId) {
        return caseMapper.selectList(
            Wrappers.<ReviewKnowledgeCase>lambdaQuery()
                .eq(ReviewKnowledgeCase::getKnowledgeId, knowledgeId)
                .orderByDesc(ReviewKnowledgeCase::getCreateTime)
        );
    }

    @Override
    public List<ReviewKnowledgePattern> queryPatterns(Long knowledgeId) {
        return patternMapper.selectList(
            Wrappers.<ReviewKnowledgePattern>lambdaQuery()
                .eq(ReviewKnowledgePattern::getKnowledgeId, knowledgeId)
                .orderByDesc(ReviewKnowledgePattern::getFrequency)
        );
    }

    @Override
    public List<ReviewKnowledgeMisjudgment> queryMisjudgments(Long knowledgeId) {
        return misjudgmentMapper.selectList(
            Wrappers.<ReviewKnowledgeMisjudgment>lambdaQuery()
                .eq(ReviewKnowledgeMisjudgment::getKnowledgeId, knowledgeId)
                .orderByDesc(ReviewKnowledgeMisjudgment::getCreateTime)
        );
    }

}
