package org.dromara.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.review.domain.ReviewStandardFocus;
import org.dromara.review.domain.bo.ReviewStandardFocusBo;
import org.dromara.review.domain.vo.ReviewStandardFocusVo;
import org.dromara.review.mapper.ReviewStandardFocusMapper;
import org.dromara.review.service.IReviewStandardFocusService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * 审核标准关注要点Service业务层处理
 *
 * @author ruoyi
 * @date 2026-06-17
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ReviewStandardFocusServiceImpl implements IReviewStandardFocusService {

    private final ReviewStandardFocusMapper baseMapper;

    @Override
    public List<ReviewStandardFocusVo> queryListByStandardId(Long standardId) {
        LambdaQueryWrapper<ReviewStandardFocus> lqw = Wrappers.lambdaQuery();
        lqw.eq(ReviewStandardFocus::getStandardId, standardId);
        lqw.orderByAsc(ReviewStandardFocus::getSortOrder);
        return baseMapper.selectVoList(lqw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(ReviewStandardFocusBo bo) {
        ReviewStandardFocus add = MapstructUtils.convert(bo, ReviewStandardFocus.class);
        if (add.getStatus() == null) {
            add.setStatus("0");
        }
        if (add.getSortOrder() == null) {
            int max = baseMapper.selectCount(
                Wrappers.<ReviewStandardFocus>lambdaQuery().eq(ReviewStandardFocus::getStandardId, add.getStandardId())
            ).intValue();
            add.setSortOrder(max + 1);
        }
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(ReviewStandardFocusBo bo) {
        ReviewStandardFocus update = MapstructUtils.convert(bo, ReviewStandardFocus.class);
        return baseMapper.updateById(update) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Collection<Long> ids) {
        return baseMapper.deleteByIds(ids) > 0;
    }

}
