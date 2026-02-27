package org.dromara.resource.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.resource.domain.BizCompanyInfo;
import org.dromara.resource.domain.bo.BizCompanyInfoBo;
import org.dromara.resource.domain.vo.BizCompanyInfoVo;
import org.dromara.resource.domain.vo.CompanyListVo;
import org.dromara.resource.mapper.BizCompanyInfoMapper;
import org.dromara.resource.service.IBizCompanyInfoService;
import org.dromara.system.domain.SysDept;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.mapper.SysDeptMapper;
import org.dromara.system.service.ISysUserService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 企业信息Service实现
 *
 * @author ruoyi
 * @date 2026-02-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizCompanyInfoServiceImpl extends ServiceImpl<BizCompanyInfoMapper, BizCompanyInfo> implements IBizCompanyInfoService {

    private final SysDeptMapper sysDeptMapper;
    private final ISysUserService userService;

    @Override
    public BizCompanyInfoVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public BizCompanyInfoVo queryByDeptId(Long deptId) {
        return baseMapper.selectByDeptId(deptId);
    }

    @Override
    public TableDataInfo<CompanyListVo> queryCompanyList(String deptName, String status, PageQuery pageQuery) {
        // 获取当前登录用户的部门ID
        Long currentDeptId = LoginHelper.getDeptId();

        // 查询当前用户所属部门及其所有子部门
        List<Long> deptIds = sysDeptMapper.selectDeptAndChildById(currentDeptId);

        LambdaQueryWrapper<SysDept> deptWrapper = Wrappers.lambdaQuery();
        deptWrapper.in(SysDept::getDeptId, deptIds);
        deptWrapper.like(ObjectUtil.isNotEmpty(deptName), SysDept::getDeptName, deptName);
        deptWrapper.eq(ObjectUtil.isNotEmpty(status), SysDept::getStatus, status);
        deptWrapper.orderByAsc(SysDept::getParentId);
        deptWrapper.orderByAsc(SysDept::getOrderNum);

        Page<SysDept> page = sysDeptMapper.selectPage(pageQuery.build(), deptWrapper);

        // 转换为 CompanyListVo
        List<CompanyListVo> voList = new ArrayList<>();
        for (SysDept dept : page.getRecords()) {
            CompanyListVo vo = new CompanyListVo();
            vo.setDeptId(dept.getDeptId());
            vo.setDeptName(dept.getDeptName());
            vo.setLeader(dept.getLeader());
            vo.setPhone(dept.getPhone());
            vo.setStatus(dept.getStatus());

            // 查询负责人名称
            if (dept.getLeader() != null) {
                SysUserVo user = userService.selectUserById(dept.getLeader());
                if (user != null) {
                    vo.setLeaderName(user.getNickName());
                }
            }

            // 查询关联的企业信息
            BizCompanyInfoVo companyInfo = baseMapper.selectByDeptId(dept.getDeptId());
            if (companyInfo != null) {
                vo.setCompanyInfoId(companyInfo.getId());
                vo.setUnifiedCreditCode(companyInfo.getUnifiedCreditCode());
                vo.setCompanyLogo(companyInfo.getCompanyLogo());
                vo.setEnterpriseAbbr(companyInfo.getEnterpriseAbbr());
            }

            // 统计数据（预留，后续实现）
            vo.setQualificationCount(0);
            vo.setPersonnelCount(0);
            vo.setCertificateCount(0);

            voList.add(vo);
        }

        Page<CompanyListVo> resultPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        resultPage.setRecords(voList);

        return TableDataInfo.build(resultPage);
    }

    @Override
    public Boolean insertByBo(BizCompanyInfoBo bo) {
        // 检查是否已存在该部门的企业信息
        BizCompanyInfoVo existing = baseMapper.selectByDeptId(bo.getDeptId());
        if (existing != null) {
            throw new ServiceException("该公司的企业信息已存在");
        }

        BizCompanyInfo add = MapstructUtils.convert(bo, BizCompanyInfo.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    @Override
    public Boolean updateByBo(BizCompanyInfoBo bo) {
        BizCompanyInfo update = MapstructUtils.convert(bo, BizCompanyInfo.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (isValid) {
            List<BizCompanyInfo> list = baseMapper.selectByIds(ids);
            if (list.size() != ids.size()) {
                throw new ServiceException("删除失败，部分数据不存在");
            }
        }
        return baseMapper.deleteByIds(ids) > 0;
    }

    private void validEntityBeforeSave(BizCompanyInfo entity) {
        // 可以在此处添加校验逻辑
    }

}
