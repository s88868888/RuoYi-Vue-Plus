package org.dromara.resource.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.resource.domain.bo.BizFinanceInfoBo;
import org.dromara.resource.domain.vo.BizFinanceInfoVo;

import java.util.Collection;
import java.util.List;

/**
 * 财务信息Service接口
 *
 * @author ruoyi
 * @date 2026-02-12
 */
public interface IBizFinanceInfoService {

    /**
     * 查询财务信息
     */
    BizFinanceInfoVo queryById(Long id);

    /**
     * 查询财务信息列表
     */
    TableDataInfo<BizFinanceInfoVo> queryPageList(BizFinanceInfoBo bo, PageQuery pageQuery);

    /**
     * 查询财务信息列表
     */
    List<BizFinanceInfoVo> queryList(BizFinanceInfoBo bo);

    /**
     * 新增财务信息
     */
    Boolean insertByBo(BizFinanceInfoBo bo);

    /**
     * 修改财务信息
     */
    Boolean updateByBo(BizFinanceInfoBo bo);

    /**
     * 校验并批量删除财务信息信息
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
