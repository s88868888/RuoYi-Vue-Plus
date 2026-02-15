package org.dromara.resource.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.resource.domain.BizCompanyInfo;
import org.dromara.resource.domain.vo.BizCompanyInfoVo;

/**
 * 企业信息Mapper接口
 *
 * @author ruoyi
 * @date 2026-02-10
 */
public interface BizCompanyInfoMapper extends BaseMapperPlus<BizCompanyInfo, BizCompanyInfoVo> {

    /**
     * 根据部门ID查询企业信息
     *
     * @param deptId 部门ID
     * @return 企业信息
     */
    BizCompanyInfoVo selectByDeptId(@Param("deptId") Long deptId);

}
