package org.dromara.resource.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.resource.domain.BizAiPromptTemplate;
import org.dromara.resource.domain.bo.BizAiPromptTemplateBo;
import org.dromara.resource.domain.vo.BizAiPromptTemplateVo;

import java.util.List;

/**
 * AI提示词模板Service接口
 *
 * @author ruoyi
 * @date 2026-02-26
 */
public interface IBizAiPromptTemplateService extends IService<BizAiPromptTemplate> {

    /**
     * 查询AI提示词模板分页列表
     *
     * @param bo       查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<BizAiPromptTemplateVo> queryPageList(BizAiPromptTemplateBo bo, PageQuery pageQuery);

    /**
     * 查询AI提示词模板列表
     *
     * @param bo 查询条件
     * @return 列表
     */
    List<BizAiPromptTemplateVo> queryList(BizAiPromptTemplateBo bo);

    /**
     * 根据ID查询AI提示词模板
     *
     * @param id 主键ID
     * @return AI提示词模板
     */
    BizAiPromptTemplateVo queryById(Long id);

    /**
     * 新增AI提示词模板
     *
     * @param bo 业务对象
     * @return 结果
     */
    Boolean insertByBo(BizAiPromptTemplateBo bo);

    /**
     * 修改AI提示词模板
     *
     * @param bo 业务对象
     * @return 结果
     */
    Boolean updateByBo(BizAiPromptTemplateBo bo);

    /**
     * 删除AI提示词模板
     *
     * @param ids 主键ID列表
     * @param isValid 是否校验
     * @return 结果
     */
    Boolean deleteWithValidByIds(List<Long> ids, Boolean isValid);

    /**
     * 根据类型查询启用的模板列表
     *
     * @param templateType 模板类型
     * @return 模板列表
     */
    List<BizAiPromptTemplateVo> queryByType(String templateType);

}
