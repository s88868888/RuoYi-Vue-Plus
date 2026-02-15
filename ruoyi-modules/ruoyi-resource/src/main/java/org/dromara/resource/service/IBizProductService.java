package org.dromara.resource.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.resource.domain.BizProduct;
import org.dromara.resource.domain.bo.BizProductBo;
import org.dromara.resource.domain.vo.BizProductVo;

import java.util.List;

/**
 * 产品信息Service接口
 *
 * @author ruoyi
 * @date 2026-02-11
 */
public interface IBizProductService extends IService<BizProduct> {

    /**
     * 查询产品信息分页列表
     *
     * @param bo       查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<BizProductVo> queryPageList(BizProductBo bo, PageQuery pageQuery);

    /**
     * 查询产品信息列表
     *
     * @param bo 查询条件
     * @return 列表
     */
    List<BizProductVo> queryList(BizProductBo bo);

    /**
     * 根据ID查询产品信息
     *
     * @param id 主键ID
     * @return 产品信息
     */
    BizProductVo queryById(Long id);

    /**
     * 新增产品信息
     *
     * @param bo 业务对象
     * @return 结果
     */
    Boolean insertByBo(BizProductBo bo);

    /**
     * 修改产品信息
     *
     * @param bo 业务对象
     * @return 结果
     */
    Boolean updateByBo(BizProductBo bo);

    /**
     * 删除产品信息
     *
     * @param ids 主键ID列表
     * @return 结果
     */
    Boolean deleteWithValidByIds(List<Long> ids, Boolean isValid);

}
