package org.dromara.resource.service.impl;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.resource.domain.BizBidSubmission;
import org.dromara.resource.domain.BizDocumentConfig;
import org.dromara.resource.domain.BizSubmissionDocument;
import org.dromara.resource.domain.BizSubmissionDocumentLog;
import org.dromara.resource.domain.bo.GenerationConfigBo;
import org.dromara.resource.domain.vo.BidSubmissionProgressVo;
import org.dromara.resource.domain.vo.BizSubmissionDocumentVo;
import org.dromara.resource.mapper.BizBidSubmissionMapper;
import org.dromara.resource.mapper.BizDocumentConfigMapper;
import org.dromara.resource.mapper.BizSubmissionDocumentLogMapper;
import org.dromara.resource.mapper.BizSubmissionDocumentMapper;
import org.dromara.resource.service.IBidDocumentGenerationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 标书文档生成Service业务层处理
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BidDocumentGenerationServiceImpl implements IBidDocumentGenerationService {

    private final BizBidSubmissionMapper submissionMapper;
    private final BizSubmissionDocumentMapper documentMapper;
    private final BizSubmissionDocumentLogMapper documentLogMapper;
    private final BizDocumentConfigMapper documentConfigMapper;

    @Async("bidGenerationExecutor")
    @Override
    public void generateDocumentsAsync(Long submissionId) {
        log.info("开始异步生成投标项目文档，submissionId: {}", submissionId);

        BizBidSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            log.error("投标项目不存在: {}", submissionId);
            return;
        }

        try {
            // 1. 查询配置表中的配置
            LambdaQueryWrapper<BizDocumentConfig> configWrapper = Wrappers.lambdaQuery();
            configWrapper.eq(BizDocumentConfig::getBidSubmissionId, submissionId);
            configWrapper.eq(BizDocumentConfig::getStatus, "active");
            configWrapper.orderByAsc(BizDocumentConfig::getCompanyId, BizDocumentConfig::getDocumentType, BizDocumentConfig::getDocumentNo);
            List<BizDocumentConfig> configs = documentConfigMapper.selectList(configWrapper);

            if (configs.isEmpty()) {
                log.warn("投标项目没有配置文档: {}", submissionId);
                submission.setSubmissionStatus("failed");
                submission.setErrorMessage("没有配置文档");
                submissionMapper.updateById(submission);
                return;
            }

            // 2. 创建文档记录
            List<BizSubmissionDocument> documents = createDocumentRecords(submission, configs);

            // 3. 更新总文档数
            submission.setTotalDocuments(documents.size());
            submissionMapper.updateById(submission);

            // 4. 记录日志
            logProgress(submissionId, null, "初始化", 0, "文档记录创建完成，共" + documents.size() + "个文档");

            // TODO: 后续实现AI生成逻辑
            log.info("文档生成功能开发中，submissionId: {}", submissionId);

            // 暂时标记为完成
            submission.setSubmissionStatus("completed");
            submission.setGenerationProgress(100);
            submission.setEndTime(new Date());
            submission.setCompletedDocuments(documents.size());
            submissionMapper.updateById(submission);

        } catch (Exception e) {
            log.error("投标项目生成失败: {}", submissionId, e);
            submission.setSubmissionStatus("failed");
            submission.setErrorMessage(e.getMessage());
            submission.setEndTime(new Date());
            submissionMapper.updateById(submission);
        }
    }

    /**
     * 创建文档记录（基于配置表）
     */
    private List<BizSubmissionDocument> createDocumentRecords(BizBidSubmission submission, List<BizDocumentConfig> configs) {
        List<BizSubmissionDocument> documents = new ArrayList<>();

        for (BizDocumentConfig config : configs) {
            BizSubmissionDocument doc = createDocument(submission, config);
            documentMapper.insert(doc);
            documents.add(doc);
        }

        return documents;
    }

    /**
     * 创建单个文档记录（基于配置表）
     */
    private BizSubmissionDocument createDocument(BizBidSubmission submission, BizDocumentConfig config) {
        BizSubmissionDocument doc = new BizSubmissionDocument();
        doc.setBidSubmissionId(submission.getId());
        doc.setDocumentConfigId(config.getId());
        doc.setCompanyId(config.getCompanyId());
        doc.setCompanyName(config.getCompanyName());

        String typeName = switch (config.getDocumentType()) {
            case "commercial" -> "商务标";
            case "technical" -> "技术标";
            case "complete" -> "整本标书";
            default -> "标书";
        };

        doc.setDocumentName(String.format("%s-%s-%s%d", submission.getProjectName(), config.getCompanyName(), typeName, config.getDocumentNo()));
        doc.setDocumentType(config.getDocumentType());
        doc.setDocumentNo(config.getDocumentNo());
        doc.setGenerationStatus("pending");
        doc.setGenerationProgress(0);
        doc.setVersion(1);
        doc.setIsLatest("1");

        return doc;
    }

    @Override
    public void generateSingleDocument(Long documentId) {
        log.info("生成单个文档，documentId: {}", documentId);
        // TODO: 后续实现AI生成单个文档的逻辑
    }

    @Override
    public BidSubmissionProgressVo getGenerationProgress(Long submissionId) {
        BizBidSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new RuntimeException("投标项目不存在");
        }

        BidSubmissionProgressVo progressVo = new BidSubmissionProgressVo();
        progressVo.setSubmissionId(submissionId);
        progressVo.setSubmissionStatus(submission.getSubmissionStatus());
        progressVo.setOverallProgress(submission.getGenerationProgress());
        progressVo.setTotalDocuments(submission.getTotalDocuments());
        progressVo.setCompletedDocuments(submission.getCompletedDocuments());
        progressVo.setFailedDocuments(submission.getFailedDocuments());

        // 查询文档列表
        LambdaQueryWrapper<BizSubmissionDocument> docWrapper = Wrappers.lambdaQuery();
        docWrapper.eq(BizSubmissionDocument::getBidSubmissionId, submissionId);
        List<BizSubmissionDocumentVo> documents = documentMapper.selectVoList(docWrapper);

        List<BidSubmissionProgressVo.DocumentProgressItem> docItems = documents.stream().map(doc -> {
            BidSubmissionProgressVo.DocumentProgressItem item = new BidSubmissionProgressVo.DocumentProgressItem();
            item.setDocumentId(doc.getId());
            item.setCompanyName(doc.getCompanyName());
            item.setDocumentType(doc.getDocumentType());
            item.setDocumentTypeName(getDocumentTypeName(doc.getDocumentType()));
            item.setDocumentNo(doc.getDocumentNo());
            item.setGenerationStatus(doc.getGenerationStatus());
            item.setStatusText(getStatusText(doc.getGenerationStatus()));
            item.setProgress(doc.getGenerationProgress());
            item.setErrorMessage(doc.getErrorMessage());
            return item;
        }).collect(Collectors.toList());

        progressVo.setDocuments(docItems);

        // 查询日志
        LambdaQueryWrapper<BizSubmissionDocumentLog> logWrapper = Wrappers.lambdaQuery();
        logWrapper.eq(BizSubmissionDocumentLog::getBidSubmissionId, submissionId);
        logWrapper.orderByDesc(BizSubmissionDocumentLog::getLogTime);
        logWrapper.last("LIMIT 50");
        List<BizSubmissionDocumentLog> logs = documentLogMapper.selectList(logWrapper);

        List<BidSubmissionProgressVo.LogItem> logItems = logs.stream().map(log -> {
            BidSubmissionProgressVo.LogItem item = new BidSubmissionProgressVo.LogItem();
            item.setTime(DateUtil.formatDateTime(log.getLogTime()));
            item.setLevel(log.getLogLevel());
            item.setMessage(log.getLogMessage());
            item.setStage(log.getStage());
            item.setProgress(log.getProgress());
            return item;
        }).collect(Collectors.toList());

        progressVo.setLogs(logItems);

        return progressVo;
    }

    @Override
    public void cancelGeneration(Long submissionId) {
        log.info("取消生成任务，submissionId: {}", submissionId);
        // TODO: 后续实现取消任务的逻辑
        logProgress(submissionId, null, "取消", 0, "用户取消生成任务");
    }

    /**
     * 记录进度日志
     */
    private void logProgress(Long submissionId, Long documentId, String stage, int progress, String message) {
        BizSubmissionDocumentLog log = new BizSubmissionDocumentLog();
        log.setBidSubmissionId(submissionId);
        log.setSubmissionDocumentId(documentId);
        log.setLogLevel("INFO");
        log.setStage(stage);
        log.setProgress(progress);
        log.setLogMessage(message);
        log.setLogTime(new Date());
        documentLogMapper.insert(log);
    }

    /**
     * 获取文档类型名称
     */
    private String getDocumentTypeName(String docType) {
        return switch (docType) {
            case "commercial" -> "商务标";
            case "technical" -> "技术标";
            case "complete" -> "整本标书";
            default -> "未知";
        };
    }

    /**
     * 获取状态文本
     */
    private String getStatusText(String status) {
        return switch (status) {
            case "pending" -> "待生成";
            case "generating" -> "生成中";
            case "completed" -> "已完成";
            case "failed" -> "失败";
            default -> "未知";
        };
    }

}
