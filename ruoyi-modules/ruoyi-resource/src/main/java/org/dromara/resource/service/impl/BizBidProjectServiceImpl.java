package org.dromara.resource.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.resource.domain.BizBidProject;
import org.dromara.resource.domain.bo.BizBidProjectBo;
import org.dromara.resource.domain.vo.BizBidProjectVo;
import org.dromara.resource.mapper.BizBidProjectMapper;
import org.dromara.resource.service.IBizBidProjectService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 招标项目Service实现
 *
 * @author ruoyi
 * @date 2026-02-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizBidProjectServiceImpl extends ServiceImpl<BizBidProjectMapper, BizBidProject> implements IBizBidProjectService {

    private final BizBidProjectMapper bizBidProjectMapper;

    @Override
    public TableDataInfo<BizBidProjectVo> queryPageList(BizBidProjectBo bo, PageQuery pageQuery) {
        Page<BizBidProjectVo> page = bizBidProjectMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(bo));
        return TableDataInfo.build(page);
    }

    @Override
    public List<BizBidProjectVo> queryList(BizBidProjectBo bo) {
        return bizBidProjectMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public BizBidProjectVo queryById(Long id) {
        return bizBidProjectMapper.selectVoById(id);
    }

    @Override
    public Boolean insertByBo(BizBidProjectBo bo) {
        BizBidProject add = MapstructUtils.convert(bo, BizBidProject.class);
        validEntityBeforeSave(add);
        boolean flag = bizBidProjectMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    @Override
    public Boolean updateByBo(BizBidProjectBo bo) {
        BizBidProject update = MapstructUtils.convert(bo, BizBidProject.class);
        validEntityBeforeSave(update);
        return bizBidProjectMapper.updateById(update) > 0;
    }

    @Override
    public Boolean deleteWithValidByIds(List<Long> ids, Boolean isValid) {
        if (isValid) {
            List<BizBidProject> list = bizBidProjectMapper.selectByIds(ids);
            if (list.size() != ids.size()) {
                throw new ServiceException("删除失败，部分数据不存在");
            }
        }
        return bizBidProjectMapper.deleteByIds(ids) > 0;
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<BizBidProject> buildQueryWrapper(BizBidProjectBo bo) {
        LambdaQueryWrapper<BizBidProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(ObjectUtil.isNotEmpty(bo.getProjectName()), BizBidProject::getProjectName, bo.getProjectName());
        wrapper.like(ObjectUtil.isNotEmpty(bo.getBidOrg()), BizBidProject::getBidOrg, bo.getBidOrg());
        wrapper.eq(ObjectUtil.isNotEmpty(bo.getProjectType()), BizBidProject::getProjectType, bo.getProjectType());
        wrapper.eq(ObjectUtil.isNotEmpty(bo.getStatus()), BizBidProject::getStatus, bo.getStatus());
        wrapper.eq(ObjectUtil.isNotEmpty(bo.getBidMethod()), BizBidProject::getBidMethod, bo.getBidMethod());
        wrapper.eq(ObjectUtil.isNotEmpty(bo.getProjectSource()), BizBidProject::getProjectSource, bo.getProjectSource());
        wrapper.ge(ObjectUtil.isNotEmpty(bo.getPublishDate()), BizBidProject::getPublishDate, bo.getPublishDate());
        wrapper.le(ObjectUtil.isNotEmpty(bo.getDeadline()), BizBidProject::getDeadline, bo.getDeadline());
        wrapper.orderByDesc(BizBidProject::getCreateTime);
        return wrapper;
    }

    /**
     * 保存前校验
     */
    private void validEntityBeforeSave(BizBidProject entity) {
        // 可以在此处添加校验逻辑
    }

}
