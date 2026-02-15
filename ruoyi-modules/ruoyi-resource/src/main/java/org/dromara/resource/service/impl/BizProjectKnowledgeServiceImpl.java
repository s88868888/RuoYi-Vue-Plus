package org.dromara.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.resource.domain.BizProjectKnowledge;
import org.dromara.resource.domain.bo.BizProjectKnowledgeBo;
import org.dromara.resource.domain.vo.BizProjectKnowledgeVo;
import org.dromara.resource.mapper.BizProjectKnowledgeMapper;
import org.dromara.resource.service.IBizProjectKnowledgeService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * 项目知识Service业务层处理
 *
 * @author ruoyi
 * @date 2026-02-12
 */
@RequiredArgsConstructor
@Service
public class BizProjectKnowledgeServiceImpl implements IBizProjectKnowledgeService {

    private final BizProjectKnowledgeMapper baseMapper;

    /**
     * 查询项目知识
     */
    @Override
    public BizProjectKnowledgeVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 查询项目知识列表
     */
    @Override
    public TableDataInfo<BizProjectKnowledgeVo> queryPageList(BizProjectKnowledgeBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BizProjectKnowledge> lqw = buildQueryWrapper(bo);
        Page<BizProjectKnowledgeVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询项目知识列表
     */
    @Override
    public List<BizProjectKnowledgeVo> queryList(BizProjectKnowledgeBo bo) {
        LambdaQueryWrapper<BizProjectKnowledge> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<BizProjectKnowledge> buildQueryWrapper(BizProjectKnowledgeBo bo) {
        LambdaQueryWrapper<BizProjectKnowledge> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getDeptId() != null, BizProjectKnowledge::getDeptId, bo.getDeptId());
        lqw.like(StringUtils.isNotBlank(bo.getKnowledgeName()), BizProjectKnowledge::getKnowledgeName, bo.getKnowledgeName());
        lqw.eq(StringUtils.isNotBlank(bo.getProjectType()), BizProjectKnowledge::getProjectType, bo.getProjectType());
        lqw.like(StringUtils.isNotBlank(bo.getDescription()), BizProjectKnowledge::getDescription, bo.getDescription());
        lqw.orderByDesc(BizProjectKnowledge::getCreateTime);
        return lqw;
    }

    /**
     * 新增项目知识
     */
    @Override
    public Boolean insertByBo(BizProjectKnowledgeBo bo) {
        BizProjectKnowledge add = MapstructUtils.convert(bo, BizProjectKnowledge.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改项目知识
     */
    @Override
    public Boolean updateByBo(BizProjectKnowledgeBo bo) {
        BizProjectKnowledge update = MapstructUtils.convert(bo, BizProjectKnowledge.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(BizProjectKnowledge entity) {
        // TODO 做一些数据校验,如唯一约束
    }

    /**
     * 批量删除项目知识
     */
    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (isValid) {
            // TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteByIds(ids) > 0;
    }
}
