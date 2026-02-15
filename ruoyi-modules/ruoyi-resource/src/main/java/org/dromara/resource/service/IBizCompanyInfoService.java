package org.dromara.resource.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.resource.domain.BizCompanyInfo;
import org.dromara.resource.domain.bo.BizCompanyInfoBo;
import org.dromara.resource.domain.vo.BizCompanyInfoVo;
import org.dromara.resource.domain.vo.CompanyListVo;

import java.util.Collection;
import java.util.List;

/**
 * 企业信息Service接口
 *
 * @author ruoyi
 * @date 2026-02-10
 */
public interface IBizCompanyInfoService extends IService<BizCompanyInfo> {

    /**
     * 查询企业信息
     *
     * @param id 主键ID
     * @return 企业信息
     */
    BizCompanyInfoVo queryById(Long id);

    /**
     * 根据部门ID查询企业信息
     *
     * @param deptId 部门ID
     * @return 企业信息
     */
    BizCompanyInfoVo queryByDeptId(Long deptId);

    /**
     * 查询公司卡片列表（分页）
     *
     * @param deptName 公司名称（模糊查询）
     * @param status   状态
     * @param pageQuery 分页参数
     * @return 公司列表
     */
    TableDataInfo<CompanyListVo> queryCompanyList(String deptName, String status, PageQuery pageQuery);

    /**
     * 新增企业信息
     *
     * @param bo 企业信息业务对象
     * @return 是否成功
     */
    Boolean insertByBo(BizCompanyInfoBo bo);

    /**
     * 修改企业信息
     *
     * @param bo 企业信息业务对象
     * @return 是否成功
     */
    Boolean updateByBo(BizCompanyInfoBo bo);

    /**
     * 校验并批量删除企业信息
     *
     * @param ids     主键ID集合
     * @param isValid 是否校验
     * @return 是否成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

}
