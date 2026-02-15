package org.dromara.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.resource.domain.BizFinanceInfo;
import org.dromara.resource.domain.bo.BizFinanceInfoBo;
import org.dromara.resource.domain.vo.BizFinanceInfoVo;
import org.dromara.resource.mapper.BizFinanceInfoMapper;
import org.dromara.resource.service.IBizFinanceInfoService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 财务信息Service业务层处理
 *
 * @author ruoyi
 * @date 2026-02-12
 */
@RequiredArgsConstructor
@Service
public class BizFinanceInfoServiceImpl implements IBizFinanceInfoService {

    private final BizFinanceInfoMapper baseMapper;

    /**
     * 查询财务信息
     */
    @Override
    public BizFinanceInfoVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 查询财务信息列表
     */
    @Override
    public TableDataInfo<BizFinanceInfoVo> queryPageList(BizFinanceInfoBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BizFinanceInfo> lqw = buildQueryWrapper(bo);
        Page<BizFinanceInfoVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询财务信息列表
     */
    @Override
    public List<BizFinanceInfoVo> queryList(BizFinanceInfoBo bo) {
        LambdaQueryWrapper<BizFinanceInfo> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<BizFinanceInfo> buildQueryWrapper(BizFinanceInfoBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<BizFinanceInfo> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getDeptId() != null, BizFinanceInfo::getDeptId, bo.getDeptId());
        lqw.like(StringUtils.isNotBlank(bo.getFinanceName()), BizFinanceInfo::getFinanceName, bo.getFinanceName());
        lqw.eq(StringUtils.isNotBlank(bo.getInfoType()), BizFinanceInfo::getInfoType, bo.getInfoType());
        lqw.between(params.get("beginFinanceDate") != null && params.get("endFinanceDate") != null,
            BizFinanceInfo::getFinanceDate,
            params.get("beginFinanceDate"), params.get("endFinanceDate"));
        lqw.orderByDesc(BizFinanceInfo::getCreateTime);
        return lqw;
    }

    /**
     * 新增财务信息
     */
    @Override
    public Boolean insertByBo(BizFinanceInfoBo bo) {
        BizFinanceInfo add = MapstructUtils.convert(bo, BizFinanceInfo.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改财务信息
     */
    @Override
    public Boolean updateByBo(BizFinanceInfoBo bo) {
        BizFinanceInfo update = MapstructUtils.convert(bo, BizFinanceInfo.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(BizFinanceInfo entity) {
        // TODO 做一些数据校验,如唯一约束
    }

    /**
     * 批量删除财务信息
     */
    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (isValid) {
            // TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteByIds(ids) > 0;
    }
}
