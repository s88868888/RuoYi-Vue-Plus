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
import org.dromara.resource.domain.BizPersonnel;
import org.dromara.resource.domain.bo.BizPersonnelBo;
import org.dromara.resource.domain.vo.BizPersonnelVo;
import org.dromara.resource.mapper.BizPersonnelMapper;
import org.dromara.resource.service.IBizPersonnelService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 人员信息Service实现
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizPersonnelServiceImpl extends ServiceImpl<BizPersonnelMapper, BizPersonnel> implements IBizPersonnelService {

    private final BizPersonnelMapper bizPersonnelMapper;

    @Override
    public TableDataInfo<BizPersonnelVo> queryPageList(BizPersonnelBo bo, PageQuery pageQuery) {
        Page<BizPersonnelVo> page = bizPersonnelMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(bo));
        return TableDataInfo.build(page);
    }

    @Override
    public List<BizPersonnelVo> queryList(BizPersonnelBo bo) {
        return bizPersonnelMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public BizPersonnelVo queryById(Long id) {
        return bizPersonnelMapper.selectVoById(id);
    }

    @Override
    public List<BizPersonnelVo> queryByDeptId(Long deptId) {
        return bizPersonnelMapper.selectByDeptId(deptId);
    }

    @Override
    public Boolean insertByBo(BizPersonnelBo bo) {
        BizPersonnel add = MapstructUtils.convert(bo, BizPersonnel.class);
        validEntityBeforeSave(add);
        boolean flag = bizPersonnelMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    @Override
    public Boolean updateByBo(BizPersonnelBo bo) {
        BizPersonnel update = MapstructUtils.convert(bo, BizPersonnel.class);
        validEntityBeforeSave(update);
        return bizPersonnelMapper.updateById(update) > 0;
    }

    @Override
    public Boolean deleteWithValidByIds(List<Long> ids, Boolean isValid) {
        if (isValid) {
            List<BizPersonnel> list = bizPersonnelMapper.selectByIds(ids);
            if (list.size() != ids.size()) {
                throw new ServiceException("删除失败，部分数据不存在");
            }
        }
        return bizPersonnelMapper.deleteByIds(ids) > 0;
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<BizPersonnel> buildQueryWrapper(BizPersonnelBo bo) {
        LambdaQueryWrapper<BizPersonnel> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(ObjectUtil.isNotEmpty(bo.getName()), BizPersonnel::getName, bo.getName());
        wrapper.eq(ObjectUtil.isNotEmpty(bo.getGender()), BizPersonnel::getGender, bo.getGender());
        wrapper.eq(ObjectUtil.isNotEmpty(bo.getIdCardType()), BizPersonnel::getIdCardType, bo.getIdCardType());
        wrapper.eq(ObjectUtil.isNotEmpty(bo.getIdCardNumber()), BizPersonnel::getIdCardNumber, bo.getIdCardNumber());
        wrapper.eq(ObjectUtil.isNotEmpty(bo.getPhone()), BizPersonnel::getPhone, bo.getPhone());
        wrapper.eq(ObjectUtil.isNotEmpty(bo.getPosition()), BizPersonnel::getPosition, bo.getPosition());
        wrapper.eq(ObjectUtil.isNotEmpty(bo.getStatus()), BizPersonnel::getStatus, bo.getStatus());
        wrapper.orderByDesc(BizPersonnel::getCreateTime);
        return wrapper;
    }

    /**
     * 保存前校验
     */
    private void validEntityBeforeSave(BizPersonnel entity) {
        // 可以在此处添加校验逻辑
    }

}
