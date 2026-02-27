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
import org.dromara.resource.domain.BizAiPromptTemplate;
import org.dromara.resource.domain.bo.BizAiPromptTemplateBo;
import org.dromara.resource.domain.vo.BizAiPromptTemplateVo;
import org.dromara.resource.mapper.BizAiPromptTemplateMapper;
import org.dromara.resource.service.IBizAiPromptTemplateService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI提示词模板Service实现
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizAiPromptTemplateServiceImpl extends ServiceImpl<BizAiPromptTemplateMapper, BizAiPromptTemplate> implements IBizAiPromptTemplateService {

    private final BizAiPromptTemplateMapper bizAiPromptTemplateMapper;

    @Override
    public TableDataInfo<BizAiPromptTemplateVo> queryPageList(BizAiPromptTemplateBo bo, PageQuery pageQuery) {
        Page<BizAiPromptTemplateVo> page = bizAiPromptTemplateMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(bo));
        return TableDataInfo.build(page);
    }

    @Override
    public List<BizAiPromptTemplateVo> queryList(BizAiPromptTemplateBo bo) {
        return bizAiPromptTemplateMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public BizAiPromptTemplateVo queryById(Long id) {
        return bizAiPromptTemplateMapper.selectVoById(id);
    }

    @Override
    public Boolean insertByBo(BizAiPromptTemplateBo bo) {
        BizAiPromptTemplate add = MapstructUtils.convert(bo, BizAiPromptTemplate.class);
        validEntityBeforeSave(add);
        boolean flag = bizAiPromptTemplateMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    @Override
    public Boolean updateByBo(BizAiPromptTemplateBo bo) {
        BizAiPromptTemplate update = MapstructUtils.convert(bo, BizAiPromptTemplate.class);
        validEntityBeforeSave(update);
        return bizAiPromptTemplateMapper.updateById(update) > 0;
    }

    @Override
    public Boolean deleteWithValidByIds(List<Long> ids, Boolean isValid) {
        if (isValid) {
            List<BizAiPromptTemplate> list = bizAiPromptTemplateMapper.selectByIds(ids);
            if (list.size() != ids.size()) {
                throw new ServiceException("删除失败，部分数据不存在");
            }
            // 校验是否为系统模板
            for (BizAiPromptTemplate template : list) {
                if ("1".equals(template.getIsSystem())) {
                    throw new ServiceException("系统模板不允许删除");
                }
            }
        }
        return bizAiPromptTemplateMapper.deleteByIds(ids) > 0;
    }

    @Override
    public List<BizAiPromptTemplateVo> queryByType(String templateType) {
        LambdaQueryWrapper<BizAiPromptTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ObjectUtil.isNotEmpty(templateType), BizAiPromptTemplate::getTemplateType, templateType);
        wrapper.eq(BizAiPromptTemplate::getStatus, "0"); // 只查询启用的模板
        wrapper.orderByAsc(BizAiPromptTemplate::getSortOrder);
        wrapper.orderByDesc(BizAiPromptTemplate::getCreateTime);
        return bizAiPromptTemplateMapper.selectVoList(wrapper);
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<BizAiPromptTemplate> buildQueryWrapper(BizAiPromptTemplateBo bo) {
        LambdaQueryWrapper<BizAiPromptTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(ObjectUtil.isNotEmpty(bo.getTemplateName()), BizAiPromptTemplate::getTemplateName, bo.getTemplateName());
        wrapper.eq(ObjectUtil.isNotEmpty(bo.getTemplateType()), BizAiPromptTemplate::getTemplateType, bo.getTemplateType());
        wrapper.eq(ObjectUtil.isNotEmpty(bo.getStatus()), BizAiPromptTemplate::getStatus, bo.getStatus());
        wrapper.eq(ObjectUtil.isNotEmpty(bo.getIsSystem()), BizAiPromptTemplate::getIsSystem, bo.getIsSystem());
        wrapper.orderByAsc(BizAiPromptTemplate::getSortOrder);
        wrapper.orderByDesc(BizAiPromptTemplate::getCreateTime);
        return wrapper;
    }

    /**
     * 保存前校验
     */
    private void validEntityBeforeSave(BizAiPromptTemplate entity) {
        // 可以添加业务校验逻辑
    }

}
