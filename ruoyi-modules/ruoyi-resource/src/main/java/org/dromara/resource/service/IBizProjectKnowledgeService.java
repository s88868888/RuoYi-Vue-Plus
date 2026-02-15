package org.dromara.resource.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.resource.domain.bo.BizProjectKnowledgeBo;
import org.dromara.resource.domain.vo.BizProjectKnowledgeVo;

import java.util.Collection;
import java.util.List;

/**
 * 项目知识Service接口
 *
 * @author ruoyi
 * @date 2026-02-12
 */
public interface IBizProjectKnowledgeService {

    /**
     * 查询项目知识
     */
    BizProjectKnowledgeVo queryById(Long id);

    /**
     * 查询项目知识列表
     */
    TableDataInfo<BizProjectKnowledgeVo> queryPageList(BizProjectKnowledgeBo bo, PageQuery pageQuery);

    /**
     * 查询项目知识列表
     */
    List<BizProjectKnowledgeVo> queryList(BizProjectKnowledgeBo bo);

    /**
     * 新增项目知识
     */
    Boolean insertByBo(BizProjectKnowledgeBo bo);

    /**
     * 修改项目知识
     */
    Boolean updateByBo(BizProjectKnowledgeBo bo);

    /**
     * 校验并批量删除项目知识信息
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
