package org.dromara.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.resource.domain.BizPatentMedal;
import org.dromara.resource.domain.bo.BizPatentMedalBo;
import org.dromara.resource.domain.vo.BizPatentMedalVo;
import org.dromara.resource.mapper.BizPatentMedalMapper;
import org.dromara.resource.service.IBizPatentMedalService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 专利奖章Service业务层处理
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@RequiredArgsConstructor
@Service
public class BizPatentMedalServiceImpl implements IBizPatentMedalService {

    private final BizPatentMedalMapper baseMapper;

    /**
     * 查询专利奖章
     */
    @Override
    public BizPatentMedalVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 查询专利奖章列表
     */
    @Override
    public TableDataInfo<BizPatentMedalVo> queryPageList(BizPatentMedalBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BizPatentMedal> lqw = buildQueryWrapper(bo);
        Page<BizPatentMedalVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询专利奖章列表
     */
    @Override
    public List<BizPatentMedalVo> queryList(BizPatentMedalBo bo) {
        LambdaQueryWrapper<BizPatentMedal> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<BizPatentMedal> buildQueryWrapper(BizPatentMedalBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<BizPatentMedal> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getDeptId() != null, BizPatentMedal::getDeptId, bo.getDeptId());
        lqw.like(StringUtils.isNotBlank(bo.getPatentName()), BizPatentMedal::getPatentName, bo.getPatentName());
        lqw.eq(StringUtils.isNotBlank(bo.getPatentType()), BizPatentMedal::getPatentType, bo.getPatentType());
        lqw.like(StringUtils.isNotBlank(bo.getPatentNumber()), BizPatentMedal::getPatentNumber, bo.getPatentNumber());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), BizPatentMedal::getStatus, bo.getStatus());
        lqw.between(params.get("beginAuthorizationDate") != null && params.get("endAuthorizationDate") != null,
            BizPatentMedal::getAuthorizationDate,
            params.get("beginAuthorizationDate"), params.get("endAuthorizationDate"));
        lqw.orderByDesc(BizPatentMedal::getCreateTime);
        return lqw;
    }

    /**
     * 新增专利奖章
     */
    @Override
    public Boolean insertByBo(BizPatentMedalBo bo) {
        BizPatentMedal add = MapstructUtils.convert(bo, BizPatentMedal.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改专利奖章
     */
    @Override
    public Boolean updateByBo(BizPatentMedalBo bo) {
        BizPatentMedal update = MapstructUtils.convert(bo, BizPatentMedal.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(BizPatentMedal entity) {
        // TODO 做一些数据校验,如唯一约束
    }

    /**
     * 批量删除专利奖章
     */
    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (isValid) {
            // TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteByIds(ids) > 0;
    }
}
