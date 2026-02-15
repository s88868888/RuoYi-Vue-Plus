package org.dromara.resource.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.resource.domain.bo.BizPerformanceBo;
import org.dromara.resource.domain.vo.BizPerformanceVo;

import java.util.Collection;
import java.util.List;

/**
 * 业绩案例Service接口
 *
 * @author ruoyi
 * @date 2026-02-11
 */
public interface IBizPerformanceService {

    /**
     * 查询业绩案例
     */
    BizPerformanceVo queryById(Long id);

    /**
     * 查询业绩案例列表
     */
    TableDataInfo<BizPerformanceVo> queryPageList(BizPerformanceBo bo, PageQuery pageQuery);

    /**
     * 查询业绩案例列表
     */
    List<BizPerformanceVo> queryList(BizPerformanceBo bo);

    /**
     * 新增业绩案例
     */
    Boolean insertByBo(BizPerformanceBo bo);

    /**
     * 修改业绩案例
     */
    Boolean updateByBo(BizPerformanceBo bo);

    /**
     * 校验并批量删除业绩案例信息
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
