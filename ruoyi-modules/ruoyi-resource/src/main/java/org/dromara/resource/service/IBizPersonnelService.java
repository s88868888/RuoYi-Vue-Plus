package org.dromara.resource.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.resource.domain.BizPersonnel;
import org.dromara.resource.domain.bo.BizPersonnelBo;
import org.dromara.resource.domain.vo.BizPersonnelVo;

import java.util.List;

/**
 * 人员信息Service接口
 *
 * @author ruoyi
 * @date 2026-02-11
 */
public interface IBizPersonnelService extends IService<BizPersonnel> {

    /**
     * 查询人员信息分页列表
     *
     * @param bo       查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<BizPersonnelVo> queryPageList(BizPersonnelBo bo, PageQuery pageQuery);

    /**
     * 查询人员信息列表
     *
     * @param bo 查询条件
     * @return 列表
     */
    List<BizPersonnelVo> queryList(BizPersonnelBo bo);

    /**
     * 根据ID查询人员信息
     *
     * @param id 主键ID
     * @return 人员信息
     */
    BizPersonnelVo queryById(Long id);

    /**
     * 根据部门ID查询人员列表
     *
     * @param deptId 部门ID
     * @return 人员列表
     */
    List<BizPersonnelVo> queryByDeptId(Long deptId);

    /**
     * 新增人员信息
     *
     * @param bo 业务对象
     * @return 结果
     */
    Boolean insertByBo(BizPersonnelBo bo);

    /**
     * 修改人员信息
     *
     * @param bo 业务对象
     * @return 结果
     */
    Boolean updateByBo(BizPersonnelBo bo);

    /**
     * 删除人员信息
     *
     * @param ids 主键ID列表
     * @return 结果
     */
    Boolean deleteWithValidByIds(List<Long> ids, Boolean isValid);

}
