package org.dromara.resource.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.resource.domain.bo.BizQualificationBo;
import org.dromara.resource.domain.vo.BizQualificationVo;

import java.util.Collection;
import java.util.List;

/**
 * 企业资质Service接口
 *
 * @author ruoyi
 * @date 2026-02-11
 */
public interface IBizQualificationService {

    /**
     * 查询企业资质
     */
    BizQualificationVo queryById(Long id);

    /**
     * 查询企业资质列表
     */
    TableDataInfo<BizQualificationVo> queryPageList(BizQualificationBo bo, PageQuery pageQuery);

    /**
     * 查询企业资质列表
     */
    List<BizQualificationVo> queryList(BizQualificationBo bo);

    /**
     * 新增企业资质
     */
    Boolean insertByBo(BizQualificationBo bo);

    /**
     * 修改企业资质
     */
    Boolean updateByBo(BizQualificationBo bo);

    /**
     * 校验并批量删除企业资质信息
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
