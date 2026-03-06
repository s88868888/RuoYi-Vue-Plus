package org.dromara.resource.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.resource.domain.bo.BizCompetitorBo;
import org.dromara.resource.domain.vo.BizCompetitorVo;

import java.util.Collection;
import java.util.List;

/**
 * 竞争公司Service接口
 *
 * @author ruoyi
 * @date 2026-03-06
 */
public interface IBizCompetitorService {

    /**
     * 查询竞争公司
     */
    BizCompetitorVo queryById(Long id);

    /**
     * 查询竞争公司分页列表
     */
    TableDataInfo<BizCompetitorVo> queryPageList(BizCompetitorBo bo, PageQuery pageQuery);

    /**
     * 查询竞争公司列表
     */
    List<BizCompetitorVo> queryList(BizCompetitorBo bo);

    /**
     * 新增竞争公司
     */
    Boolean insertByBo(BizCompetitorBo bo);

    /**
     * 修改竞争公司
     */
    Boolean updateByBo(BizCompetitorBo bo);

    /**
     * 校验并批量删除竞争公司信息
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
