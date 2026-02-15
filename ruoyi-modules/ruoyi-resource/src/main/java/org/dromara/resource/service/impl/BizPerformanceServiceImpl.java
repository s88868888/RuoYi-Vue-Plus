package org.dromara.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.resource.domain.BizPerformance;
import org.dromara.resource.domain.bo.BizPerformanceBo;
import org.dromara.resource.domain.vo.BizPerformanceVo;
import org.dromara.resource.mapper.BizPerformanceMapper;
import org.dromara.resource.service.IBizPerformanceService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 业绩案例Service业务层处理
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@RequiredArgsConstructor
@Service
public class BizPerformanceServiceImpl implements IBizPerformanceService {

    private final BizPerformanceMapper baseMapper;

    @Override
    public BizPerformanceVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public TableDataInfo<BizPerformanceVo> queryPageList(BizPerformanceBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BizPerformance> lqw = buildQueryWrapper(bo);
        Page<BizPerformanceVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    @Override
    public List<BizPerformanceVo> queryList(BizPerformanceBo bo) {
        LambdaQueryWrapper<BizPerformance> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<BizPerformance> buildQueryWrapper(BizPerformanceBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<BizPerformance> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getDeptId() != null, BizPerformance::getDeptId, bo.getDeptId());
        lqw.like(StringUtils.isNotBlank(bo.getName()), BizPerformance::getName, bo.getName());
        lqw.eq(StringUtils.isNotBlank(bo.getPerformanceCategory()), BizPerformance::getPerformanceCategory, bo.getPerformanceCategory());
        lqw.like(StringUtils.isNotBlank(bo.getOwnerUnitName()), BizPerformance::getOwnerUnitName, bo.getOwnerUnitName());
        lqw.like(StringUtils.isNotBlank(bo.getProjectManager()), BizPerformance::getProjectManager, bo.getProjectManager());
        lqw.like(StringUtils.isNotBlank(bo.getProjectLocation()), BizPerformance::getProjectLocation, bo.getProjectLocation());
        lqw.eq(StringUtils.isNotBlank(bo.getProcessType()), BizPerformance::getProcessType, bo.getProcessType());

        // 日期范围查询
        lqw.between(params.get("beginBidDate") != null && params.get("endBidDate") != null,
            BizPerformance::getBidDate,
            params.get("beginBidDate"), params.get("endBidDate"));
        lqw.between(params.get("beginSigningDate") != null && params.get("endSigningDate") != null,
            BizPerformance::getSigningDate,
            params.get("beginSigningDate"), params.get("endSigningDate"));
        lqw.between(params.get("beginStartDate") != null && params.get("endStartDate") != null,
            BizPerformance::getStartDate,
            params.get("beginStartDate"), params.get("endStartDate"));
        lqw.between(params.get("beginCompletionDate") != null && params.get("endCompletionDate") != null,
            BizPerformance::getCompletionDate,
            params.get("beginCompletionDate"), params.get("endCompletionDate"));

        // 金额范围查询
        lqw.ge(params.get("minContractAmount") != null, BizPerformance::getContractAmount, params.get("minContractAmount"));
        lqw.le(params.get("maxContractAmount") != null, BizPerformance::getContractAmount, params.get("maxContractAmount"));

        lqw.orderByDesc(BizPerformance::getCreateTime);
        return lqw;
    }

    @Override
    public Boolean insertByBo(BizPerformanceBo bo) {
        BizPerformance add = MapstructUtils.convert(bo, BizPerformance.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    @Override
    public Boolean updateByBo(BizPerformanceBo bo) {
        BizPerformance update = MapstructUtils.convert(bo, BizPerformance.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    private void validEntityBeforeSave(BizPerformance entity) {
        // TODO 做一些数据校验,如唯一约束
    }

    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (isValid) {
            // TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteByIds(ids) > 0;
    }
}
