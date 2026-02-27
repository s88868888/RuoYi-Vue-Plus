package org.dromara.resource.service.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.resource.domain.BizSubmissionChapter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档合成Agent
 * 负责将所有章节合成为最终的标书文档
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentAssemblyAgent {

    /**
     * 合成文档
     *
     * @param chapters 所有章节（按顺序）
     * @param projectName 项目名称
     * @return 完整的标书文档（Markdown格式）
     */
    public String assembleDocument(List<BizSubmissionChapter> chapters, String projectName) {
        log.info("开始合成文档，章节数: {}", chapters.size());

        StringBuilder document = new StringBuilder();

        // 1. 添加封面
        document.append(generateCover(projectName));
        document.append("\n\n---\n\n");

        // 2. 添加目录
        document.append(generateTableOfContents(chapters));
        document.append("\n\n---\n\n");

        // 3. 添加所有章节内容
        for (BizSubmissionChapter chapter : chapters) {
            if (chapter.getChapterContent() != null && !chapter.getChapterContent().isEmpty()) {
                document.append(generateChapterSection(chapter));
                document.append("\n\n");
            }
        }

        // 4. 添加页脚
        document.append("\n\n---\n\n");
        document.append(generateFooter());

        log.info("文档合成完成，总字数: {}", document.length());
        return document.toString();
    }

    /**
     * 生成封面
     */
    private String generateCover(String projectName) {
        return String.format("""
            <div style="text-align: center; padding: 100px 0;">

            # %s

            ## 投标文件

            <br><br><br>

            **投标单位：__________________**

            **投标日期：__________________**

            </div>
            """, projectName);
    }

    /**
     * 生成目录
     */
    private String generateTableOfContents(List<BizSubmissionChapter> chapters) {
        StringBuilder toc = new StringBuilder();
        toc.append("# 目录\n\n");

        // 只显示前3级章节
        List<BizSubmissionChapter> tocChapters = chapters.stream()
            .filter(c -> c.getChapterLevel() <= 3)
            .collect(Collectors.toList());

        for (BizSubmissionChapter chapter : tocChapters) {
            String indent = "  ".repeat(chapter.getChapterLevel() - 1);
            toc.append(indent)
                .append("- ")
                .append(chapter.getChapterNo())
                .append(" ")
                .append(chapter.getChapterTitle())
                .append("\n");
        }

        return toc.toString();
    }

    /**
     * 生成章节部分
     */
    private String generateChapterSection(BizSubmissionChapter chapter) {
        StringBuilder section = new StringBuilder();

        // 章节标题（使用Markdown标题级别）
        String headerPrefix = "#".repeat(Math.min(chapter.getChapterLevel(), 6));
        section.append(headerPrefix)
            .append(" ")
            .append(chapter.getChapterNo())
            .append(" ")
            .append(chapter.getChapterTitle())
            .append("\n\n");

        // 原因说明（如果有）
        if (chapter.getReasonDescription() != null && !chapter.getReasonDescription().isEmpty()) {
            section.append(chapter.getReasonDescription())
                .append("\n\n");
        }

        // 章节内容
        section.append(chapter.getChapterContent());

        return section.toString();
    }

    /**
     * 生成页脚
     */
    private String generateFooter() {
        return """
            <div style="text-align: center; color: #666; font-size: 12px;">

            本投标文件由AI辅助生成，所有信息真实有效。

            投标单位（盖章）：__________________

            日期：__________________

            </div>
            """;
    }

    /**
     * 转换为HTML格式
     */
    public String convertToHtml(String markdown) {
        // TODO: 使用Markdown解析库转换为HTML
        // 可以使用 commonmark-java 或其他库
        return markdown;
    }

    /**
     * 转换为PDF格式
     */
    public byte[] convertToPdf(String markdown) {
        // TODO: 使用PDF生成库
        // 方案1: Markdown -> HTML -> PDF (使用 flying-saucer 或 openhtmltopdf)
        // 方案2: 使用 Apache PDFBox 直接生成
        // 方案3: 调用外部工具如 wkhtmltopdf
        return new byte[0];
    }

    /**
     * 转换为Word格式
     */
    public byte[] convertToWord(String markdown) {
        // TODO: 使用Apache POI生成Word文档
        return new byte[0];
    }

}
