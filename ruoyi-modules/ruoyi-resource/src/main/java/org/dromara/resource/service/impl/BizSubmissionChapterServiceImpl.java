package org.dromara.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.resource.domain.BizSubmissionChapter;
import org.dromara.resource.domain.vo.BizSubmissionChapterVo;
import org.dromara.resource.mapper.BizSubmissionChapterMapper;
import org.dromara.resource.service.IBizSubmissionChapterService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 标书章节Service业务层处理
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizSubmissionChapterServiceImpl implements IBizSubmissionChapterService {

    private final BizSubmissionChapterMapper baseMapper;

    @Override
    public BizSubmissionChapterVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public List<BizSubmissionChapterVo> getChapterTree(Long submissionId, Long documentId) {
        LambdaQueryWrapper<BizSubmissionChapter> lqw = Wrappers.lambdaQuery();
        if (submissionId != null) {
            lqw.eq(BizSubmissionChapter::getBidSubmissionId, submissionId);
        }
        if (documentId != null) {
            lqw.eq(BizSubmissionChapter::getSubmissionDocumentId, documentId);
        }
        lqw.orderByAsc(BizSubmissionChapter::getSortOrder);
        List<BizSubmissionChapterVo> all = baseMapper.selectVoList(lqw);
        return buildTree(all, 0L);
    }

    private List<BizSubmissionChapterVo> buildTree(List<BizSubmissionChapterVo> all, Long parentId) {
        return all.stream()
            .filter(item -> parentId.equals(item.getParentId()))
            .peek(item -> item.setChildren(buildTree(all, item.getId())))
            .collect(Collectors.toList());
    }

    @Override
    public void generateChapter(Long id) {
        BizSubmissionChapter chapter = baseMapper.selectById(id);
        if (chapter == null) {
            return;
        }
        chapter.setGenerationStatus("generating");
        chapter.setGenerationProgress(0);
        baseMapper.updateById(chapter);
        log.info("开始生成章节内容，章节ID: {}", id);
    }

    @Override
    public void fillTemplate(Long id) {
        BizSubmissionChapter chapter = baseMapper.selectById(id);
        if (chapter == null) {
            return;
        }
        chapter.setGenerationStatus("generating");
        chapter.setGenerationProgress(0);
        baseMapper.updateById(chapter);
        log.info("开始填充模板章节，章节ID: {}", id);
    }

    @Override
    public void saveChapterContent(Long id, String content) {
        BizSubmissionChapter chapter = baseMapper.selectById(id);
        if (chapter == null) {
            return;
        }
        chapter.setChapterContent(content);
        chapter.setGenerationStatus("completed");
        chapter.setGenerationProgress(100);
        baseMapper.updateById(chapter);
    }

    @Override
    public void regenerateChapter(Long id) {
        BizSubmissionChapter chapter = baseMapper.selectById(id);
        if (chapter == null) {
            return;
        }
        chapter.setGenerationStatus("generating");
        chapter.setGenerationProgress(0);
        chapter.setChapterContent(null);
        chapter.setErrorMessage(null);
        baseMapper.updateById(chapter);
        log.info("开始重新生成章节内容，章节ID: {}", id);
    }

    @Override
    public Boolean deleteById(Long id) {
        return baseMapper.deleteById(id) > 0;
    }

    @Override
    public void addChapter(Long submissionDocumentId, Long parentId, String chapterTitle, String chapterType) {
        BizSubmissionChapter chapter = new BizSubmissionChapter();
        chapter.setSubmissionDocumentId(submissionDocumentId);
        chapter.setParentId(parentId);
        chapter.setChapterTitle(chapterTitle);
        chapter.setChapterType(chapterType);
        chapter.setGenerationStatus("pending");
        chapter.setGenerationProgress(0);
        chapter.setSortOrder(0);
        chapter.setChapterLevel(1);
        baseMapper.insert(chapter);
    }

}
