package org.dromara.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.review.domain.ReviewPromptTemplate;
import org.dromara.review.domain.bo.ReviewPromptTemplateBo;
import org.dromara.review.domain.vo.ReviewPromptTemplateVo;
import org.dromara.review.mapper.ReviewPromptTemplateMapper;
import org.dromara.review.service.IReviewPromptTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * AI提示词模板Service业务层处理
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ReviewPromptTemplateServiceImpl implements IReviewPromptTemplateService {

    private final ReviewPromptTemplateMapper baseMapper;

    /**
     * 查询AI提示词模板
     */
    @Override
    public ReviewPromptTemplateVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 按类型查询AI提示词模板列表
     */
    @Override
    public List<ReviewPromptTemplateVo> queryListByType(String type) {
        LambdaQueryWrapper<ReviewPromptTemplate> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(type), ReviewPromptTemplate::getType, type);
        lqw.orderByDesc(ReviewPromptTemplate::getCreateTime);
        return baseMapper.selectVoList(lqw);
    }

    /**
     * 新增AI提示词模板
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(ReviewPromptTemplateBo bo) {
        checkTypeUnique(bo.getType(), null);
        ReviewPromptTemplate add = MapstructUtils.convert(bo, ReviewPromptTemplate.class);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            if (add != null) {
                bo.setId(add.getId());
            }
        }
        return flag;
    }

    /**
     * 修改AI提示词模板
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(ReviewPromptTemplateBo bo) {
        checkTypeUnique(bo.getType(), bo.getId());
        ReviewPromptTemplate update = MapstructUtils.convert(bo, ReviewPromptTemplate.class);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 校验并批量删除AI提示词模板信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        return baseMapper.deleteByIds(ids) > 0;
    }

    private void checkTypeUnique(String type, Long excludeId) {
        if (StringUtils.isBlank(type)) {
            return;
        }
        LambdaQueryWrapper<ReviewPromptTemplate> lqw = Wrappers.lambdaQuery();
        lqw.eq(ReviewPromptTemplate::getType, type);
        lqw.ne(excludeId != null, ReviewPromptTemplate::getId, excludeId);
        if (baseMapper.exists(lqw)) {
            throw new RuntimeException("类型编码【" + type + "】已存在，请使用其他编码");
        }
    }

}
