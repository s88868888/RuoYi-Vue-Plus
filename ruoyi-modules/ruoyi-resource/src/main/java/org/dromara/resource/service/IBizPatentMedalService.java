package org.dromara.resource.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.resource.domain.bo.BizPatentMedalBo;
import org.dromara.resource.domain.vo.BizPatentMedalVo;

import java.util.Collection;
import java.util.List;

/**
 * 专利奖章Service接口
 *
 * @author ruoyi
 * @date 2026-02-11
 */
public interface IBizPatentMedalService {

    /**
     * 查询专利奖章
     */
    BizPatentMedalVo queryById(Long id);

    /**
     * 查询专利奖章列表
     */
    TableDataInfo<BizPatentMedalVo> queryPageList(BizPatentMedalBo bo, PageQuery pageQuery);

    /**
     * 查询专利奖章列表
     */
    List<BizPatentMedalVo> queryList(BizPatentMedalBo bo);

    /**
     * 新增专利奖章
     */
    Boolean insertByBo(BizPatentMedalBo bo);

    /**
     * 修改专利奖章
     */
    Boolean updateByBo(BizPatentMedalBo bo);

    /**
     * 校验并批量删除专利奖章信息
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
