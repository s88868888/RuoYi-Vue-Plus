package org.dromara.resource.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.resource.domain.BizBidSubmission;
import org.dromara.resource.domain.BizDocumentConfig;
import org.dromara.resource.domain.bo.BizDocumentConfigBo;
import org.dromara.resource.domain.vo.BizDocumentConfigVo;
import org.dromara.resource.mapper.BizBidSubmissionMapper;
import org.dromara.resource.mapper.BizDocumentConfigMapper;
import org.dromara.resource.service.IBizDocumentConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 标书配置Service业务层处理
 *
 * @author ruoyi
 * @date 2026-03-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizDocumentConfigServiceImpl implements IBizDocumentConfigService {

    private final BizDocumentConfigMapper baseMapper;
    private final BizBidSubmissionMapper bidSubmissionMapper;

    @Override
    public List<BizDocumentConfigVo> queryBySubmissionId(Long submissionId) {
        LambdaQueryWrapper<BizDocumentConfig> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(BizDocumentConfig::getBidSubmissionId, submissionId);
        wrapper.eq(BizDocumentConfig::getStatus, "active");
        wrapper.orderByAsc(BizDocumentConfig::getCompanyId, BizDocumentConfig::getDocumentType, BizDocumentConfig::getDocumentNo);
        return baseMapper.selectVoList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void batchSaveConfigs(Long submissionId, List<BizDocumentConfigBo> configs) {
        // 1. 软删除旧配置
        LambdaUpdateWrapper<BizDocumentConfig> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.eq(BizDocumentConfig::getBidSubmissionId, submissionId);
        updateWrapper.set(BizDocumentConfig::getStatus, "deleted");
        baseMapper.update(null, updateWrapper);

        // 2. 插入新配置
        for (BizDocumentConfigBo bo : configs) {
            bo.setBidSubmissionId(submissionId);
            bo.setStatus("active");
            BizDocumentConfig entity = MapstructUtils.convert(bo, BizDocumentConfig.class);
            baseMapper.insert(entity);
        }

        // 3. 同步更新父级投标项目状态
        syncSubmissionConfigStatus(submissionId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long addConfig(BizDocumentConfigBo bo) {
        bo.setStatus("active");
        BizDocumentConfig entity = MapstructUtils.convert(bo, BizDocumentConfig.class);
        baseMapper.insert(entity);

        // 同步更新父级投标项目状态
        syncSubmissionConfigStatus(bo.getBidSubmissionId());

        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean deleteConfig(Long configId) {
        // 先查询获取 submissionId
        BizDocumentConfig config = baseMapper.selectById(configId);
        Long submissionId = config != null ? config.getBidSubmissionId() : null;

        LambdaUpdateWrapper<BizDocumentConfig> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.eq(BizDocumentConfig::getId, configId);
        updateWrapper.set(BizDocumentConfig::getStatus, "deleted");
        boolean result = baseMapper.update(null, updateWrapper) > 0;

        // 同步更新父级投标项目状态
        if (result && submissionId != null) {
            syncSubmissionConfigStatus(submissionId);
        }

        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean deleteBySubmissionId(Long submissionId) {
        LambdaUpdateWrapper<BizDocumentConfig> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.eq(BizDocumentConfig::getBidSubmissionId, submissionId);
        updateWrapper.set(BizDocumentConfig::getStatus, "deleted");
        boolean result = baseMapper.update(null, updateWrapper) > 0;

        // 同步更新父级投标项目状态
        syncSubmissionConfigStatus(submissionId);

        return result;
    }

    /**
     * 同步更新父级投标项目的 workflowStage 和 selectedCompanies
     */
    private void syncSubmissionConfigStatus(Long submissionId) {
        if (submissionId == null) return;

        BizBidSubmission submission = bidSubmissionMapper.selectById(submissionId);
        if (submission == null) return;

        // 查询当前所有有效配置
        List<BizDocumentConfigVo> configs = queryBySubmissionId(submissionId);

        if (!configs.isEmpty()) {
            // 从配置中提取去重的公司列表
            List<Map<String, Object>> companyList = configs.stream()
                .filter(c -> StringUtils.isNotBlank(c.getCompanyName()))
                .map(BizDocumentConfigVo::getCompanyName)
                .distinct()
                .map(name -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("companyName", name);
                    return map;
                })
                .collect(Collectors.toList());
            submission.setSelectedCompanies(JSON.toJSONString(companyList));

            // 如果当前是 pending_config，更新为 configured
            if ("pending_config".equals(submission.getWorkflowStage())) {
                submission.setWorkflowStage("configured");
            }
        } else {
            // 无配置，重置
            submission.setSelectedCompanies("[]");
            if ("configured".equals(submission.getWorkflowStage())) {
                submission.setWorkflowStage("pending_config");
            }
        }

        bidSubmissionMapper.updateById(submission);
    }

}
