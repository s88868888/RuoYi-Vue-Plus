package org.dromara.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.resource.domain.BizCompetitor;
import org.dromara.resource.domain.bo.BizCompetitorBo;
import org.dromara.resource.domain.vo.BizCompetitorVo;
import org.dromara.resource.mapper.BizCompetitorMapper;
import org.dromara.resource.service.IBizCompetitorService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * 竞争公司Service业务层处理
 *
 * @author ruoyi
 * @date 2026-03-06
 */
@RequiredArgsConstructor
@Service
public class BizCompetitorServiceImpl implements IBizCompetitorService {

    private final BizCompetitorMapper baseMapper;

    @Override
    public BizCompetitorVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public TableDataInfo<BizCompetitorVo> queryPageList(BizCompetitorBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BizCompetitor> lqw = buildQueryWrapper(bo);
        Page<BizCompetitorVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    @Override
    public List<BizCompetitorVo> queryList(BizCompetitorBo bo) {
        LambdaQueryWrapper<BizCompetitor> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<BizCompetitor> buildQueryWrapper(BizCompetitorBo bo) {
        LambdaQueryWrapper<BizCompetitor> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getDeptId() != null, BizCompetitor::getDeptId, bo.getDeptId());
        lqw.like(StringUtils.isNotBlank(bo.getCompanyName()), BizCompetitor::getCompanyName, bo.getCompanyName());
        lqw.eq(StringUtils.isNotBlank(bo.getCompanyType()), BizCompetitor::getCompanyType, bo.getCompanyType());
        lqw.eq(StringUtils.isNotBlank(bo.getCompetitorLevel()), BizCompetitor::getCompetitorLevel, bo.getCompetitorLevel());
        lqw.like(StringUtils.isNotBlank(bo.getProvince()), BizCompetitor::getProvince, bo.getProvince());
        lqw.orderByDesc(BizCompetitor::getCreateTime);
        return lqw;
    }

    @Override
    public Boolean insertByBo(BizCompetitorBo bo) {
        BizCompetitor add = MapstructUtils.convert(bo, BizCompetitor.class);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    @Override
    public Boolean updateByBo(BizCompetitorBo bo) {
        BizCompetitor update = MapstructUtils.convert(bo, BizCompetitor.class);
        return baseMapper.updateById(update) > 0;
    }

    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        return baseMapper.deleteByIds(ids) > 0;
    }
}
