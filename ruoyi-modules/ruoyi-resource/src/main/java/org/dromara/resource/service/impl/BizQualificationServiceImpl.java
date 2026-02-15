package org.dromara.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.resource.domain.BizQualification;
import org.dromara.resource.domain.bo.BizQualificationBo;
import org.dromara.resource.domain.vo.BizQualificationVo;
import org.dromara.resource.mapper.BizQualificationMapper;
import org.dromara.resource.service.IBizQualificationService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 企业资质Service业务层处理
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@RequiredArgsConstructor
@Service
public class BizQualificationServiceImpl implements IBizQualificationService {

    private final BizQualificationMapper baseMapper;

    /**
     * 查询企业资质
     */
    @Override
    public BizQualificationVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 查询企业资质列表
     */
    @Override
    public TableDataInfo<BizQualificationVo> queryPageList(BizQualificationBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BizQualification> lqw = buildQueryWrapper(bo);
        Page<BizQualificationVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询企业资质列表
     */
    @Override
    public List<BizQualificationVo> queryList(BizQualificationBo bo) {
        LambdaQueryWrapper<BizQualification> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<BizQualification> buildQueryWrapper(BizQualificationBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<BizQualification> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getDeptId() != null, BizQualification::getDeptId, bo.getDeptId());
        lqw.like(StringUtils.isNotBlank(bo.getCertNumber()), BizQualification::getCertNumber, bo.getCertNumber());
        lqw.like(StringUtils.isNotBlank(bo.getCertName()), BizQualification::getCertName, bo.getCertName());
        lqw.eq(StringUtils.isNotBlank(bo.getCertCategory()), BizQualification::getCertCategory, bo.getCertCategory());
        lqw.eq(StringUtils.isNotBlank(bo.getCertStatus()), BizQualification::getCertStatus, bo.getCertStatus());
        lqw.between(params.get("beginValidStartDate") != null && params.get("endValidStartDate") != null,
            BizQualification::getValidStartDate,
            params.get("beginValidStartDate"), params.get("endValidStartDate"));
        lqw.between(params.get("beginValidEndDate") != null && params.get("endValidEndDate") != null,
            BizQualification::getValidEndDate,
            params.get("beginValidEndDate"), params.get("endValidEndDate"));
        lqw.orderByDesc(BizQualification::getCreateTime);
        return lqw;
    }

    /**
     * 新增企业资质
     */
    @Override
    public Boolean insertByBo(BizQualificationBo bo) {
        BizQualification add = MapstructUtils.convert(bo, BizQualification.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改企业资质
     */
    @Override
    public Boolean updateByBo(BizQualificationBo bo) {
        BizQualification update = MapstructUtils.convert(bo, BizQualification.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(BizQualification entity) {
        // TODO 做一些数据校验,如唯一约束
    }

    /**
     * 批量删除企业资质
     */
    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (isValid) {
            // TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteByIds(ids) > 0;
    }
}
