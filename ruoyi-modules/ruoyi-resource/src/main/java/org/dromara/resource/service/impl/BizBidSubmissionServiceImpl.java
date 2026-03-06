package org.dromara.resource.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.linpeilie.Converter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.resource.domain.BizBidProject;
import org.dromara.resource.domain.BizBidSubmission;
import org.dromara.resource.domain.BizSubmissionChapter;
import org.dromara.resource.domain.bo.BizBidSubmissionBo;
import org.dromara.resource.domain.bo.GenerationConfigBo;
import org.dromara.resource.domain.vo.BizBidSubmissionVo;
import org.dromara.resource.domain.vo.BidSubmissionProgressVo;
import org.dromara.resource.domain.vo.BizSubmissionChapterVo;
import org.dromara.resource.mapper.BizBidProjectMapper;
import org.dromara.resource.mapper.BizBidSubmissionMapper;
import org.dromara.resource.mapper.BizSubmissionChapterMapper;
import org.dromara.resource.service.IBidDocumentGenerationService;
import org.dromara.resource.service.IAiAnalysisService;
import org.dromara.resource.service.IBizBidSubmissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 投标项目Service业务层处理
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizBidSubmissionServiceImpl implements IBizBidSubmissionService {

    private final BizBidSubmissionMapper baseMapper;
    private final BizBidProjectMapper bidProjectMapper;
    private final BizSubmissionChapterMapper chapterMapper;
    private final IBidDocumentGenerationService documentGenerationService;
    private final IAiAnalysisService aiAnalysisService;
    private final Converter converter;

    @Override
    public BizBidSubmissionVo queryById(Long id) {
        BizBidSubmissionVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getBidProjectId() != null) {
            BizBidProject project = bidProjectMapper.selectById(vo.getBidProjectId());
            if (project != null) {
                vo.setPublishDate(project.getPublishDate());
                vo.setDeadline(project.getDeadline());
            }
        }
        return vo;
    }

    @Override
    public TableDataInfo<BizBidSubmissionVo> queryPageList(BizBidSubmissionBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BizBidSubmission> lqw = buildQueryWrapper(bo);
        Page<BizBidSubmissionVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    @Override
    public List<BizBidSubmissionVo> queryList(BizBidSubmissionBo bo) {
        LambdaQueryWrapper<BizBidSubmission> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<BizBidSubmission> buildQueryWrapper(BizBidSubmissionBo bo) {
        LambdaQueryWrapper<BizBidSubmission> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getBidProjectId() != null, BizBidSubmission::getBidProjectId, bo.getBidProjectId());
        lqw.like(StringUtils.isNotBlank(bo.getProjectName()), BizBidSubmission::getProjectName, bo.getProjectName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), BizBidSubmission::getStatus, bo.getStatus());
        lqw.orderByDesc(BizBidSubmission::getCreateTime);
        return lqw;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(BizBidSubmissionBo bo) {
        BizBidSubmission add = converter.convert(bo, BizBidSubmission.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(BizBidSubmissionBo bo) {
        BizBidSubmission update = converter.convert(bo, BizBidSubmission.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    private void validEntityBeforeSave(BizBidSubmission entity) {
        // 可以添加业务校验逻辑
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (isValid) {
            // 可以添加删除前的校验逻辑
        }
        return baseMapper.deleteByIds(ids) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFromBidProject(Long bidProjectId, BizBidSubmissionBo bo) {
        // 1. 查询招标项目信息
        BizBidProject bidProject = bidProjectMapper.selectById(bidProjectId);
        if (bidProject == null) {
            throw new RuntimeException("招标项目不存在");
        }

        // 2. 从招标项目提取信息
        BizBidSubmission submission = new BizBidSubmission();
        submission.setBidProjectId(bidProjectId);
        submission.setProjectName(bidProject.getProjectName());
        submission.setBidOrg(bidProject.getBidOrg());
        submission.setProjectType(bidProject.getProjectType());
        submission.setBudgetAmount(bidProject.getBudgetAmount());
        submission.setProjectRegion(bidProject.getProjectRegion());
        submission.setBidMethod(bidProject.getBidMethod());
        submission.setProjectDesc(bidProject.getProjectDesc());

        // 3. 设置生成配置
        submission.setSelectedCompanies(bo.getSelectedCompanies());
        submission.setGenerationConfig(bo.getGenerationConfig());

        // 4. 计算总文档数
        int totalDocs = calculateTotalDocuments(bo.getGenerationConfig());
        submission.setTotalDocuments(totalDocs);

        // 5. 初始化状态
        submission.setStatus("draft");
        submission.setGenerationProgress(0);
        submission.setCompletedDocuments(0);
        submission.setFailedDocuments(0);

        // 初始化竞争对手分析状态（若选择分析则设为 analyzing，实际异步触发由 Controller 在事务提交后执行）
        if (Boolean.TRUE.equals(bo.getAnalyzeCompetitors())) {
            submission.setCompetitorAnalysisStatus("analyzing");
        } else {
            submission.setCompetitorAnalysisStatus("none");
        }

        submission.setRemark(bo.getRemark());

        // 6. 保存投标项目
        baseMapper.insert(submission);

        return submission.getId();
    }

    /**
     * 计算总文档数
     */
    private int calculateTotalDocuments(String generationConfigJson) {
        List<GenerationConfigBo> configs = JSON.parseArray(generationConfigJson, GenerationConfigBo.class);
        int total = 0;
        for (GenerationConfigBo config : configs) {
            total += ObjectUtil.defaultIfNull(config.getCommercial(), 0);
            total += ObjectUtil.defaultIfNull(config.getTechnical(), 0);
            total += ObjectUtil.defaultIfNull(config.getComplete(), 0);
        }
        return total;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean startGeneration(Long submissionId) {
        BizBidSubmission submission = baseMapper.selectById(submissionId);
        if (submission == null) {
            throw new RuntimeException("投标项目不存在");
        }

        // 检查状态
        if ("generating".equals(submission.getStatus())) {
            throw new RuntimeException("投标项目正在生成中，请勿重复操作");
        }

        // 更新状态为生成中
        submission.setStatus("generating");
        submission.setGenerationProgress(0);
        submission.setStartTime(new Date());
        submission.setErrorMessage(null);
        baseMapper.updateById(submission);

        // 异步生成文档
        documentGenerationService.generateDocumentsAsync(submissionId);

        return true;
    }

    @Override
    public BidSubmissionProgressVo getProgress(Long submissionId) {
        return documentGenerationService.getGenerationProgress(submissionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean cancelGeneration(Long submissionId) {
        BizBidSubmission submission = baseMapper.selectById(submissionId);
        if (submission == null) {
            throw new RuntimeException("投标项目不存在");
        }

        // 如果已经不在生成中（可能异步任务已完成或已取消），直接返回成功
        if (!"generating".equals(submission.getStatus())) {
            log.info("投标项目[{}]当前状态为'{}'，非生成中，无需取消", submissionId, submission.getStatus());
            return true;
        }

        // 取消生成任务
        documentGenerationService.cancelGeneration(submissionId);

        // 取消后回到已配置状态
        submission.setStatus("configured");
        submission.setGenerationProgress(0);
        submission.setEndTime(new Date());
        baseMapper.updateById(submission);

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean regenerate(Long submissionId) {
        BizBidSubmission submission = baseMapper.selectById(submissionId);
        if (submission == null) {
            throw new RuntimeException("投标项目不存在");
        }

        // 重置状态
        submission.setStatus("configured");
        submission.setGenerationProgress(0);
        submission.setCompletedDocuments(0);
        submission.setFailedDocuments(0);
        submission.setStartTime(null);
        submission.setEndTime(null);
        submission.setErrorMessage(null);
        baseMapper.updateById(submission);

        // 开始生成
        return startGeneration(submissionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean saveStep1Config(BizBidSubmissionBo bo) {
        BizBidSubmission submission = baseMapper.selectById(bo.getId());
        if (submission == null) {
            throw new RuntimeException("投标项目不存在");
        }
        submission.setSelectedCompanies(bo.getSelectedCompanies());
        submission.setGenerationConfig(bo.getGenerationConfig());
        if (bo.getGenerationConfig() != null) {
            int totalDocs = calculateTotalDocuments(bo.getGenerationConfig());
            submission.setTotalDocuments(totalDocs);
        }
        // 仅当当前为 draft 时，标记为已配置
        if ("draft".equals(submission.getStatus())) {
            submission.setStatus("configured");
        }
        return baseMapper.updateById(submission) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean generateChapterStructure(Long submissionId) {
        BizBidSubmission submission = baseMapper.selectById(submissionId);
        if (submission == null) {
            throw new RuntimeException("投标项目不存在");
        }
        // 章节结构由 DocumentConfig 追踪，不再改顶层状态
        baseMapper.updateById(submission);
        return true;
    }

    @Override
    public List<BizSubmissionChapterVo> getChapterTree(Long submissionId) {
        List<BizSubmissionChapterVo> allChapters = chapterMapper.selectVoList(
            Wrappers.<BizSubmissionChapter>lambdaQuery()
                .eq(BizSubmissionChapter::getBidSubmissionId, submissionId)
                .orderByAsc(BizSubmissionChapter::getSortOrder)
        );
        return buildTree(allChapters, 0L);
    }

    private List<BizSubmissionChapterVo> buildTree(List<BizSubmissionChapterVo> all, Long parentId) {
        List<BizSubmissionChapterVo> tree = new ArrayList<>();
        for (BizSubmissionChapterVo chapter : all) {
            if (Objects.equals(chapter.getParentId(), parentId)) {
                chapter.setChildren(buildTree(all, chapter.getId()));
                tree.add(chapter);
            }
        }
        return tree;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean startContentGeneration(Long submissionId) {
        return startGeneration(submissionId);
    }

    @Override
    public void exportDocument(Long submissionId, HttpServletResponse response) {
        BizBidSubmission submission = baseMapper.selectById(submissionId);
        if (submission == null) {
            throw new RuntimeException("投标项目不存在");
        }
        if (!"generated".equals(submission.getStatus())) {
            throw new RuntimeException("标书尚未生成完成，无法导出");
        }
        // TODO: 实现标书文件导出逻辑
        log.info("导出标书文件: submissionId={}", submissionId);
    }

}
