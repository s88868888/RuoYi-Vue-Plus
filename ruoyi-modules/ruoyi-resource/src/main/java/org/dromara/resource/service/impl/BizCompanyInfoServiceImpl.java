package org.dromara.resource.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.service.AsyncVectorSyncService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.resource.domain.*;
import org.dromara.resource.domain.bo.BizCompanyInfoBo;
import org.dromara.resource.domain.vo.*;
import org.dromara.resource.mapper.*;
import org.dromara.resource.service.IBizCompanyInfoService;
import org.dromara.system.domain.SysDept;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.mapper.SysDeptMapper;
import org.dromara.system.service.ISysUserService;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

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
    private final AsyncVectorSyncService asyncVectorSyncService;
    private final BizPersonnelMapper personnelMapper;
    private final BizProductMapper productMapper;
    private final BizQualificationMapper qualificationMapper;
    private final BizPerformanceMapper performanceMapper;
    private final BizPatentMedalMapper patentMedalMapper;
    private final BizFinanceInfoMapper financeInfoMapper;
    private final BizProjectKnowledgeMapper projectKnowledgeMapper;

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

    @Override
    public int syncAllToVector() {
        Long currentDeptId = LoginHelper.getDeptId();
        String tenantId = LoginHelper.getTenantId();
        List<Long> deptIds = sysDeptMapper.selectDeptAndChildById(currentDeptId);

        int syncCount = 0;
        for (Long deptId : deptIds) {
            syncDeptToVector(tenantId, deptId);
            syncCount++;
        }
        log.info("触发同步 {} 个企业信息到向量库", syncCount);
        return syncCount;
    }

    @Override
    public void syncToVector(Long deptId) {
        String tenantId = LoginHelper.getTenantId();
        BizCompanyInfoVo companyInfo = baseMapper.selectByDeptId(deptId);
        if (companyInfo == null) {
            throw new ServiceException("该公司暂无企业信息，无法同步");
        }
        syncDeptToVector(tenantId, deptId);
        log.info("触发同步企业信息到向量库: deptId={}", deptId);
    }

    private void syncDeptToVector(String tenantId, Long deptId) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        // 公司基本信息
        BizCompanyInfoVo companyInfo = baseMapper.selectByDeptId(deptId);
        if (companyInfo != null) {
            SysDept dept = sysDeptMapper.selectById(deptId);
            String companyName = dept != null ? dept.getDeptName() : "";
            asyncVectorSyncService.asyncSyncCompanyInfo(tenantId, deptId, buildCompanyDataMap(companyInfo, companyName));
        }

        // 人员信息
        List<BizPersonnelVo> personnelList = personnelMapper.selectByDeptId(deptId);
        for (BizPersonnelVo p : personnelList) {
            Map<String, Object> data = new LinkedHashMap<>();
            putIfNotNull(data, "name", p.getName());
            putIfNotNull(data, "position", p.getPosition());
            putIfNotNull(data, "gender", p.getGender());
            putIfNotNull(data, "workYears", p.getWorkYears());
            putIfNotNull(data, "status", p.getStatus());
            putIfNotNull(data, "hireDate", p.getHireDate() != null ? sdf.format(p.getHireDate()) : null);
            asyncVectorSyncService.asyncSyncPersonnelInfo(tenantId, deptId, data);
        }

        // 产品信息
        List<BizProductVo> productList = productMapper.selectVoList(
            Wrappers.<BizProduct>lambdaQuery().eq(BizProduct::getDeptId, deptId));
        for (BizProductVo p : productList) {
            Map<String, Object> data = new LinkedHashMap<>();
            putIfNotNull(data, "productName", p.getProductName());
            putIfNotNull(data, "productModel", p.getProductModel());
            putIfNotNull(data, "productCategory", p.getProductCategory());
            putIfNotNull(data, "performanceDesc", p.getPerformanceDesc());
            putIfNotNull(data, "quantity", p.getQuantity());
            asyncVectorSyncService.asyncSyncProductInfo(tenantId, deptId, data);
        }

        // 资质信息
        List<BizQualificationVo> qualList = qualificationMapper.selectVoList(
            Wrappers.<BizQualification>lambdaQuery().eq(BizQualification::getDeptId, deptId));
        for (BizQualificationVo q : qualList) {
            Map<String, Object> data = new LinkedHashMap<>();
            putIfNotNull(data, "qualificationName", q.getCertName());
            putIfNotNull(data, "qualificationType", q.getCertCategory());
            putIfNotNull(data, "certificateNo", q.getCertNumber());
            putIfNotNull(data, "issuingAuthority", q.getIssuingAuthority());
            putIfNotNull(data, "certStatus", q.getCertStatus());
            putIfNotNull(data, "validUntil", q.getValidEndDate() != null ? sdf.format(q.getValidEndDate()) : null);
            asyncVectorSyncService.asyncSyncQualificationInfo(tenantId, deptId, data);
        }

        // 业绩案例
        List<BizPerformanceVo> perfList = performanceMapper.selectVoList(
            Wrappers.<BizPerformance>lambdaQuery().eq(BizPerformance::getDeptId, deptId));
        for (BizPerformanceVo p : perfList) {
            Map<String, Object> data = new LinkedHashMap<>();
            putIfNotNull(data, "projectName", p.getName());
            putIfNotNull(data, "performanceCategory", p.getPerformanceCategory());
            putIfNotNull(data, "projectStatus", p.getProjectStatus());
            putIfNotNull(data, "contractAmount", p.getContractAmount());
            putIfNotNull(data, "projectContent", p.getProjectContent());
            putIfNotNull(data, "projectLocation", p.getProjectLocation());
            putIfNotNull(data, "completionDate", p.getCompletionDate() != null ? sdf.format(p.getCompletionDate()) : null);
            asyncVectorSyncService.asyncSyncPerformanceCase(tenantId, deptId, data);
        }

        // 专利奖章
        List<BizPatentMedalVo> patentList = patentMedalMapper.selectVoList(
            Wrappers.<BizPatentMedal>lambdaQuery().eq(BizPatentMedal::getDeptId, deptId));
        for (BizPatentMedalVo p : patentList) {
            Map<String, Object> data = new LinkedHashMap<>();
            putIfNotNull(data, "patentName", p.getPatentName());
            putIfNotNull(data, "patentType", p.getPatentType());
            putIfNotNull(data, "patentNo", p.getPatentNumber());
            putIfNotNull(data, "patentee", p.getPatentee());
            putIfNotNull(data, "inventor", p.getInventor());
            putIfNotNull(data, "field", p.getField());
            putIfNotNull(data, "abstract", p.getPatentAbstract());
            asyncVectorSyncService.asyncSyncPatentInfo(tenantId, deptId, data);
        }

        // 财务信息
        List<BizFinanceInfoVo> financeList = financeInfoMapper.selectVoList(
            Wrappers.<BizFinanceInfo>lambdaQuery().eq(BizFinanceInfo::getDeptId, deptId));
        for (BizFinanceInfoVo f : financeList) {
            Map<String, Object> data = new LinkedHashMap<>();
            putIfNotNull(data, "financeName", f.getFinanceName());
            putIfNotNull(data, "infoType", f.getInfoType());
            putIfNotNull(data, "financeDate", f.getFinanceDate() != null ? sdf.format(f.getFinanceDate()) : null);
            putIfNotNull(data, "remark", f.getRemark());
            asyncVectorSyncService.asyncSyncFinancialInfo(tenantId, deptId, data);
        }

        // 项目知识
        List<BizProjectKnowledgeVo> knowledgeList = projectKnowledgeMapper.selectVoList(
            Wrappers.<BizProjectKnowledge>lambdaQuery().eq(BizProjectKnowledge::getDeptId, deptId));
        for (BizProjectKnowledgeVo k : knowledgeList) {
            Map<String, Object> data = new LinkedHashMap<>();
            putIfNotNull(data, "title", k.getKnowledgeName());
            putIfNotNull(data, "content", k.getDescription());
            putIfNotNull(data, "projectType", k.getProjectType());
            asyncVectorSyncService.asyncSyncProjectKnowledge(tenantId, deptId, data);
        }
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
    private Map<String, Object> buildCompanyDataMap(BizCompanyInfoVo vo, String companyName) {
        Map<String, Object> data = new LinkedHashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        data.put("companyName", companyName);
        if (vo.getUnifiedCreditCode() != null) {
            data.put("creditCode", vo.getUnifiedCreditCode());
        }
        if (vo.getLegalPerson() != null) {
            data.put("legalPerson", vo.getLegalPerson());
        }
        if (vo.getRegisteredCapital() != null) {
            data.put("registeredCapital", vo.getRegisteredCapital());
        }
        if (vo.getEnterpriseNature() != null) {
            data.put("enterpriseNature", vo.getEnterpriseNature());
        }
        if (vo.getEstablishmentDate() != null) {
            data.put("establishDate", sdf.format(vo.getEstablishmentDate()));
        }
        if (vo.getBusinessScope() != null) {
            data.put("businessScope", vo.getBusinessScope());
        }
        if (vo.getEnterpriseScale() != null) {
            data.put("enterpriseScale", vo.getEnterpriseScale());
        }
        if (vo.getIndustryCategory() != null) {
            data.put("industryCategory", vo.getIndustryCategory());
        }
        if (vo.getCompanyAddress() != null) {
            data.put("companyAddress", vo.getCompanyAddress());
        }
        if (vo.getEnterpriseRegion() != null) {
            data.put("enterpriseRegion", vo.getEnterpriseRegion());
        }
        if (vo.getRegisteredAddress() != null) {
            data.put("registeredAddress", vo.getRegisteredAddress());
        }
        if (vo.getTotalEmployees() != null) {
            data.put("totalEmployees", vo.getTotalEmployees());
        }
        if (vo.getManagementCertification() != null) {
            data.put("managementCertification", vo.getManagementCertification());
        }
        if (vo.getAnnualProductionCapacity() != null) {
            data.put("annualProductionCapacity", vo.getAnnualProductionCapacity());
        }

        return data;
    }
}
