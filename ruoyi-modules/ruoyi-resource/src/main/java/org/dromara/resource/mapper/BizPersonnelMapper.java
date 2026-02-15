package org.dromara.resource.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.resource.domain.BizPersonnel;
import org.dromara.resource.domain.vo.BizPersonnelVo;

import java.util.List;
import java.util.Map;

/**
 * 人员信息Mapper接口
 *
 * @author ruoyi
 * @date 2026-02-11
 */
public interface BizPersonnelMapper extends BaseMapperPlus<BizPersonnel, BizPersonnelVo> {

    /**
     * 根据部门ID查询人员列表
     *
     * @param deptId 部门ID
     * @return 人员列表
     */
    List<BizPersonnelVo> selectByDeptId(@Param("deptId") Long deptId);

    /**
     * 根据部门ID统计人员数量
     *
     * @param deptId 部门ID
     * @return 数量
     */
    Long countByDeptId(@Param("deptId") Long deptId);

}
