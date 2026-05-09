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
import org.dromara.review.domain.bo.ReviewKnowledgeBo;
import org.dromara.review.domain.vo.ReviewKnowledgeVo;
import org.dromara.review.domain.vo.ReviewStandardVo;
import org.dromara.review.mapper.ReviewKnowledgeCaseMapper;
import org.dromara.review.mapper.ReviewKnowledgeMapper;
import org.dromara.review.mapper.ReviewKnowledgeMisjudgmentMapper;
import org.dromara.review.mapper.ReviewKnowledgePatternMapper;
import org.dromara.review.mapper.ReviewStandardKnowledgeMapper;
import org.dromara.review.mapper.ReviewStandardMapper;
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
        return standardMapper.selectVoByIds(standardIds);
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
