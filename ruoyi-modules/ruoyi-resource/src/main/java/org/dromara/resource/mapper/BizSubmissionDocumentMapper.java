package org.dromara.resource.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.resource.domain.BizSubmissionDocument;
import org.dromara.resource.domain.vo.BizSubmissionDocumentVo;

import java.util.List;

/**
 * 标书文档Mapper接口
 *
 * @author ruoyi
 * @date 2026-02-26
 */
public interface BizSubmissionDocumentMapper extends BaseMapperPlus<BizSubmissionDocument, BizSubmissionDocumentVo> {

    /**
     * 查询指定投标项目最新版本文档列表
     */
    List<BizSubmissionDocumentVo> selectLatestBySubmissionId(@Param("submissionId") Long submissionId);

    /**
     * 查询某配置的所有版本（按 version 倒序）
     */
    List<BizSubmissionDocumentVo> selectVersionsByConfigId(@Param("documentConfigId") Long documentConfigId);

}
