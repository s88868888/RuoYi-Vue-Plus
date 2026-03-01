package org.dromara.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.resource.domain.BizDocumentConfig;
import org.dromara.resource.domain.bo.BizDocumentConfigBo;
import org.dromara.resource.domain.vo.BizDocumentConfigVo;
import org.dromara.resource.mapper.BizDocumentConfigMapper;
import org.dromara.resource.service.IBizDocumentConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    }

    @Override
    public Long addConfig(BizDocumentConfigBo bo) {
        bo.setStatus("active");
        BizDocumentConfig entity = MapstructUtils.convert(bo, BizDocumentConfig.class);
        baseMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public Boolean deleteConfig(Long configId) {
        LambdaUpdateWrapper<BizDocumentConfig> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.eq(BizDocumentConfig::getId, configId);
        updateWrapper.set(BizDocumentConfig::getStatus, "deleted");
        return baseMapper.update(null, updateWrapper) > 0;
    }

    @Override
    public Boolean deleteBySubmissionId(Long submissionId) {
        LambdaUpdateWrapper<BizDocumentConfig> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.eq(BizDocumentConfig::getBidSubmissionId, submissionId);
        updateWrapper.set(BizDocumentConfig::getStatus, "deleted");
        return baseMapper.update(null, updateWrapper) > 0;
    }

}
