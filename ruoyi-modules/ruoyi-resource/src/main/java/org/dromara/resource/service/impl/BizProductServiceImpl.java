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
import org.dromara.resource.domain.BizProduct;
import org.dromara.resource.domain.bo.BizProductBo;
import org.dromara.resource.domain.vo.BizProductVo;
import org.dromara.resource.mapper.BizProductMapper;
import org.dromara.resource.service.IBizProductService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 产品信息Service实现
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizProductServiceImpl extends ServiceImpl<BizProductMapper, BizProduct> implements IBizProductService {

    private final BizProductMapper bizProductMapper;

    @Override
    public TableDataInfo<BizProductVo> queryPageList(BizProductBo bo, PageQuery pageQuery) {
        Page<BizProductVo> page = bizProductMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(bo));
        return TableDataInfo.build(page);
    }

    @Override
    public List<BizProductVo> queryList(BizProductBo bo) {
        return bizProductMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public BizProductVo queryById(Long id) {
        return bizProductMapper.selectVoById(id);
    }

    @Override
    public Boolean insertByBo(BizProductBo bo) {
        BizProduct add = MapstructUtils.convert(bo, BizProduct.class);
        validEntityBeforeSave(add);
        boolean flag = bizProductMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    @Override
    public Boolean updateByBo(BizProductBo bo) {
        BizProduct update = MapstructUtils.convert(bo, BizProduct.class);
        validEntityBeforeSave(update);
        return bizProductMapper.updateById(update) > 0;
    }

    @Override
    public Boolean deleteWithValidByIds(List<Long> ids, Boolean isValid) {
        if (isValid) {
            List<BizProduct> list = bizProductMapper.selectByIds(ids);
            if (list.size() != ids.size()) {
                throw new ServiceException("删除失败，部分数据不存在");
            }
        }
        return bizProductMapper.deleteByIds(ids) > 0;
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<BizProduct> buildQueryWrapper(BizProductBo bo) {
        LambdaQueryWrapper<BizProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(ObjectUtil.isNotEmpty(bo.getProductName()), BizProduct::getProductName, bo.getProductName());
        wrapper.like(ObjectUtil.isNotEmpty(bo.getProductModel()), BizProduct::getProductModel, bo.getProductModel());
        wrapper.eq(ObjectUtil.isNotEmpty(bo.getHasPurchaseContract()), BizProduct::getHasPurchaseContract, bo.getHasPurchaseContract());
        wrapper.eq(ObjectUtil.isNotEmpty(bo.getProductCategory()), BizProduct::getProductCategory, bo.getProductCategory());
        wrapper.orderByDesc(BizProduct::getCreateTime);
        return wrapper;
    }

    /**
     * 保存前校验
     */
    private void validEntityBeforeSave(BizProduct entity) {
        // 可以在此处添加校验逻辑
    }

}
