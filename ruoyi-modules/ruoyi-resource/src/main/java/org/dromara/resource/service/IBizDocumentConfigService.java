package org.dromara.resource.service;

import org.dromara.resource.domain.bo.BizDocumentConfigBo;
import org.dromara.resource.domain.vo.BizDocumentConfigVo;

import java.util.List;

/**
 * 标书配置Service接口
 *
 * @author ruoyi
 * @date 2026-03-01
 */
public interface IBizDocumentConfigService {

    /**
     * 查询投标项目的所有配置
     */
    List<BizDocumentConfigVo> queryBySubmissionId(Long submissionId);

    /**
     * 批量保存配置（先删除旧配置，再插入新配置）
     */
    void batchSaveConfigs(Long submissionId, List<BizDocumentConfigBo> configs);

    /**
     * 添加单个配置
     */
    Long addConfig(BizDocumentConfigBo bo);

    /**
     * 删除配置（软删除）
     */
    Boolean deleteConfig(Long configId);

    /**
     * 删除投标项目的所有配置
     */
    Boolean deleteBySubmissionId(Long submissionId);

}
