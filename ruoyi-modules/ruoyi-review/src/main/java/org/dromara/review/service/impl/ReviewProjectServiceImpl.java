package org.dromara.review.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.core.utils.file.FileUtils;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.entity.UploadResult;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.review.domain.ReviewResultItem;
import org.dromara.review.domain.ReviewStandardRule;
import org.dromara.review.domain.ReviewTask;
import org.dromara.review.domain.ReviewTaskFile;
import org.dromara.review.domain.ReviewTaskStandard;
import org.dromara.review.mapper.ReviewResultItemMapper;
import org.dromara.review.mapper.ReviewStandardMapper;
import org.dromara.review.mapper.ReviewStandardRuleMapper;
import org.dromara.review.mapper.ReviewTaskFileMapper;
import org.dromara.review.mapper.ReviewTaskMapper;
import org.dromara.review.mapper.ReviewTaskStandardMapper;
import org.dromara.review.service.IReviewProjectService;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.service.ISysOssService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 审核任务「工程包」导出 / 导入实现。
 * <p>
 * 设计要点见 {@link IReviewProjectService}。导出按 taskId 把四张表 + 附件原件字节流式写 zip；
 * 导入解 zip → 附件重传 OSS 拿新 ossId → 全新落库（id/tenant/审计字段全置空交框架重填），
 * 子表 taskId 重映射。导入任务清空 sourceId/sourceType 与 parentTaskId：避免与原系统同源任务
 * 在后续「重新审核」(createTask 去重合并) 时互相覆盖，导入件作为独立记录存在。
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ReviewProjectServiceImpl implements IReviewProjectService {

    private final ReviewTaskMapper baseMapper;
    private final ReviewTaskStandardMapper reviewTaskStandardMapper;
    private final ReviewTaskFileMapper reviewTaskFileMapper;
    private final ReviewResultItemMapper reviewResultItemMapper;
    private final ReviewStandardMapper reviewStandardMapper;
    private final ReviewStandardRuleMapper reviewStandardRuleMapper;
    private final ISysOssService ossService;

    /** 工程包格式标识，导入时校验前缀，便于将来升版本兼容 */
    private static final String FORMAT = "review-project/1.0";
    /** 解压后总字节上限，防 zip 炸弹 */
    private static final long MAX_TOTAL_BYTES = 1024L * 1024L * 1024L; // 1GB
    /** 任务目录匹配：tasks/001/ */
    private static final Pattern TASK_DIR = Pattern.compile("^tasks/\\d+/");

    // ==================== 导出 ====================

    @Override
    public void exportProject(List<Long> taskIds, HttpServletResponse response) {
        if (CollUtil.isEmpty(taskIds)) {
            throw new ServiceException("未选择任何任务");
        }
        String zipName = "审核工程包_" + DateUtil.format(new Date(), "yyyyMMdd-HHmmss") + ".zip";
        response.setContentType("application/zip");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        FileUtils.setAttachmentResponseHeader(response, zipName);

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            // manifest
            JSONObject manifest = new JSONObject();
            manifest.put("format", FORMAT);
            manifest.put("exportTime", DateUtil.formatDateTime(new Date()));
            manifest.put("taskCount", taskIds.size());
            writeEntry(zos, "manifest.json", JSON.toJSONBytes(manifest));

            int idx = 1;
            for (Long taskId : taskIds) {
                try {
                    ReviewTask task = baseMapper.selectById(taskId);
                    if (task == null) {
                        log.warn("[ReviewProject] 导出跳过：任务不存在 taskId={}", taskId);
                        continue;
                    }
                    String dir = String.format("tasks/%03d/", idx++);
                    writeEntry(zos, dir + "task.json", JSON.toJSONBytes(task));

                    // 关联标准 + 规则快照（跨库兜底展示用）
                    List<Long> standardIds = reviewTaskStandardMapper.selectList(
                            Wrappers.<ReviewTaskStandard>lambdaQuery().eq(ReviewTaskStandard::getTaskId, taskId))
                        .stream().map(ReviewTaskStandard::getStandardId).distinct().collect(Collectors.toList());
                    JSONObject std = new JSONObject();
                    std.put("standardIds", standardIds);
                    std.put("rules", rulesSnapshot(standardIds));
                    writeEntry(zos, dir + "standards.json", JSON.toJSONBytes(std));

                    // 审核结果明细
                    List<ReviewResultItem> items = reviewResultItemMapper.selectList(
                        Wrappers.<ReviewResultItem>lambdaQuery()
                            .eq(ReviewResultItem::getTaskId, taskId)
                            .orderByAsc(ReviewResultItem::getSortOrder));
                    writeEntry(zos, dir + "results.json", JSON.toJSONBytes(items));

                    // 附件原件 + searchable PDF
                    List<ReviewTaskFile> files = reviewTaskFileMapper.selectList(
                        Wrappers.<ReviewTaskFile>lambdaQuery()
                            .eq(ReviewTaskFile::getTaskId, taskId)
                            .orderByAsc(ReviewTaskFile::getSortOrder));
                    JSONArray metaArr = new JSONArray();
                    int fi = 0;
                    for (ReviewTaskFile f : files) {
                        JSONObject m = new JSONObject();
                        m.put("fileName", f.getFileName());
                        m.put("fileType", f.getFileType());
                        m.put("fileSize", f.getFileSize());
                        m.put("sortOrder", f.getSortOrder());
                        m.put("parseStatus", f.getParseStatus());
                        m.put("ocrStatus", f.getOcrStatus());
                        m.put("extractedText", f.getExtractedText());

                        byte[] bytes = downloadFileBytes(f);
                        if (bytes != null && bytes.length > 0) {
                            String entryName = fi + "_" + safeName(f.getFileName());
                            writeEntry(zos, dir + "attachments/" + entryName, bytes);
                            m.put("file", entryName);
                        }
                        if (StringUtils.isNotBlank(f.getSearchableUrl())) {
                            byte[] sb = downloadUrlBytes(f.getSearchableUrl());
                            if (sb != null && sb.length > 0) {
                                String sName = fi + "_searchable.pdf";
                                writeEntry(zos, dir + "attachments/" + sName, sb);
                                m.put("searchable", sName);
                            }
                        }
                        metaArr.add(m);
                        fi++;
                    }
                    writeEntry(zos, dir + "attachments/meta.json", JSON.toJSONBytes(metaArr));
                } catch (Exception e) {
                    // 单任务失败不影响其余任务导出
                    log.error("[ReviewProject] 导出任务失败 taskId={}: {}", taskId, e.getMessage(), e);
                }
            }
            zos.finish();
        } catch (Exception e) {
            log.error("[ReviewProject] 导出工程包失败: {}", e.getMessage(), e);
            throw new ServiceException("导出失败: " + e.getMessage());
        }
    }

    /** 规则快照：按 getToolRules 同口径取（排除停用 status=1），跨库 standardId 对不上时供展示 */
    private List<ReviewStandardRule> rulesSnapshot(List<Long> standardIds) {
        if (CollUtil.isEmpty(standardIds)) {
            return new ArrayList<>();
        }
        return reviewStandardRuleMapper.selectList(
            Wrappers.<ReviewStandardRule>lambdaQuery()
                .in(ReviewStandardRule::getStandardId, standardIds)
                .ne(ReviewStandardRule::getStatus, "1")
                .orderByAsc(ReviewStandardRule::getSortOrder));
    }

    private void writeEntry(ZipOutputStream zos, String name, byte[] data) throws java.io.IOException {
        zos.putNextEntry(new ZipEntry(name));
        if (data != null) {
            zos.write(data);
        }
        zos.closeEntry();
    }

    /** 按 ossId 下载附件字节，缺失时退回 filePath(http) */
    private byte[] downloadFileBytes(ReviewTaskFile f) {
        try {
            if (f.getOssId() != null && f.getOssId() > 0) {
                SysOssVo oss = TenantHelper.ignore(() -> ossService.getById(f.getOssId()));
                if (oss != null) {
                    OssClient storage = OssFactory.instance(oss.getService());
                    Path p = storage.fileDownload(oss.getFileName());
                    try {
                        return Files.readAllBytes(p);
                    } finally {
                        try { Files.deleteIfExists(p); } catch (Exception ignore) { }
                    }
                }
            }
            if (StringUtils.isNotBlank(f.getFilePath()) && f.getFilePath().startsWith("http")) {
                return downloadUrlBytes(f.getFilePath());
            }
        } catch (Exception e) {
            log.warn("[ReviewProject] 附件下载失败 fileId={}, ossId={}: {}", f.getId(), f.getOssId(), e.getMessage());
        }
        return null;
    }

    private byte[] downloadUrlBytes(String url) {
        try {
            URLConnection conn = URI.create(encodeUrl(url)).toURL().openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            try (InputStream in = conn.getInputStream()) {
                return in.readAllBytes();
            }
        } catch (Exception e) {
            log.warn("[ReviewProject] URL 下载失败 url={}: {}", url, e.getMessage());
            return null;
        }
    }

    /** 对路径中的非 ASCII 段做 percent-encoding（保留 scheme/分隔符），避免中文 URL 触发 400 */
    private String encodeUrl(String rawUrl) {
        StringBuilder sb = new StringBuilder(rawUrl.length());
        for (char c : rawUrl.toCharArray()) {
            if (c > 127) {
                byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                for (byte b : bytes) {
                    sb.append('%').append(String.format("%02X", b & 0xFF));
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ==================== 导入 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> importProject(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        Map<String, byte[]> entries = readZip(file);
        byte[] manifestBytes = entries.get("manifest.json");
        if (manifestBytes == null) {
            throw new ServiceException("不是有效的审核工程包（缺 manifest.json）");
        }
        JSONObject manifest = JSON.parseObject(new String(manifestBytes, StandardCharsets.UTF_8));
        String format = manifest.getString("format");
        if (format == null || !format.startsWith("review-project/")) {
            throw new ServiceException("工程包格式不支持：" + format);
        }

        // 收集任务目录 tasks/NNN/，有序导入
        TreeSet<String> dirs = new TreeSet<>();
        for (String k : entries.keySet()) {
            Matcher m = TASK_DIR.matcher(k);
            if (m.find() && m.start() == 0) {
                dirs.add(k.substring(0, m.end()));
            }
        }
        if (dirs.isEmpty()) {
            throw new ServiceException("工程包内无任务数据");
        }

        List<Long> newIds = new ArrayList<>();
        for (String dir : dirs) {
            byte[] tb = entries.get(dir + "task.json");
            if (tb == null) {
                continue;
            }
            ReviewTask task = JSON.parseObject(new String(tb, StandardCharsets.UTF_8), ReviewTask.class);
            resetTaskIdentity(task);
            baseMapper.insert(task);
            Long newTaskId = task.getId();
            newIds.add(newTaskId);

            importStandards(entries.get(dir + "standards.json"), newTaskId);
            importResults(entries.get(dir + "results.json"), newTaskId);
            importAttachments(entries, dir, newTaskId);
        }
        log.info("[ReviewProject] 导入完成，新建任务 {} 条: {}", newIds.size(), newIds);
        return newIds;
    }

    /** 任务身份重置：新记录、当前租户/用户、断开原系统来源与父任务链 */
    private void resetTaskIdentity(ReviewTask task) {
        task.setId(null);
        task.setTenantId(null);
        task.setCreateBy(null);
        task.setCreateDept(null);
        task.setCreateTime(null);
        task.setUpdateBy(null);
        task.setUpdateTime(null);
        task.setParentTaskId(null);
        task.setSourceId(null);
        task.setSourceType(null);
        if (StringUtils.isNotBlank(task.getTaskName()) && !task.getTaskName().contains("(导入)")) {
            task.setTaskName(task.getTaskName() + "(导入)");
        }
        if (task.getVersion() == null) {
            task.setVersion(1);
        }
    }

    private void importStandards(byte[] data, Long newTaskId) {
        if (data == null) {
            return;
        }
        JSONObject std = JSON.parseObject(new String(data, StandardCharsets.UTF_8));
        List<Long> standardIds = std.getList("standardIds", Long.class);
        if (CollUtil.isEmpty(standardIds)) {
            return;
        }
        for (Long sid : standardIds) {
            // 仅当目标库存在该标准时才建关联，否则「查看规则」按快照展示，不挂死链
            if (sid != null && reviewStandardMapper.selectById(sid) != null) {
                ReviewTaskStandard rts = new ReviewTaskStandard();
                rts.setTaskId(newTaskId);
                rts.setStandardId(sid);
                reviewTaskStandardMapper.insert(rts);
            }
        }
    }

    private void importResults(byte[] data, Long newTaskId) {
        if (data == null) {
            return;
        }
        List<ReviewResultItem> items = JSON.parseArray(new String(data, StandardCharsets.UTF_8), ReviewResultItem.class);
        if (CollUtil.isEmpty(items)) {
            return;
        }
        for (ReviewResultItem it : items) {
            it.setId(null);
            it.setTaskId(newTaskId);
            it.setTenantId(null);
            it.setCreateBy(null);
            it.setCreateDept(null);
            it.setCreateTime(null);
            it.setUpdateBy(null);
            it.setUpdateTime(null);
            reviewResultItemMapper.insert(it);
        }
    }

    private void importAttachments(Map<String, byte[]> entries, String dir, Long newTaskId) {
        byte[] mb = entries.get(dir + "attachments/meta.json");
        if (mb == null) {
            return;
        }
        JSONArray metaArr = JSON.parseArray(new String(mb, StandardCharsets.UTF_8));
        int sort = 1;
        for (int i = 0; i < metaArr.size(); i++) {
            JSONObject m = metaArr.getJSONObject(i);
            ReviewTaskFile tf = new ReviewTaskFile();
            tf.setTaskId(newTaskId);
            tf.setFileName(m.getString("fileName"));
            tf.setFileType(m.getString("fileType"));
            tf.setFileSize(m.getLong("fileSize"));
            tf.setParseStatus(m.getString("parseStatus"));
            tf.setExtractedText(m.getString("extractedText"));
            tf.setOcrStatus(m.getString("ocrStatus"));
            tf.setSortOrder(sort++);

            // 原件：重传 OSS 拿新 ossId + url
            String fileEntry = m.getString("file");
            if (StringUtils.isNotBlank(fileEntry)) {
                byte[] bytes = entries.get(dir + "attachments/" + fileEntry);
                if (bytes != null && bytes.length > 0) {
                    SysOssVo vo = uploadBytes(bytes, m.getString("fileName"));
                    if (vo != null) {
                        tf.setOssId(vo.getOssId());
                        tf.setFilePath(vo.getUrl());
                    }
                }
            }
            // searchable PDF：无 sys_oss 行，仅回填 url（与 Word→PDF 同款）
            String se = m.getString("searchable");
            if (StringUtils.isNotBlank(se)) {
                byte[] sbytes = entries.get(dir + "attachments/" + se);
                if (sbytes != null && sbytes.length > 0) {
                    String url = uploadSearchable(sbytes);
                    if (StringUtils.isNotBlank(url)) {
                        tf.setSearchableUrl(url);
                        if (StringUtils.isBlank(tf.getOcrStatus())) {
                            tf.setOcrStatus("SUCCESS");
                        }
                    }
                }
            }
            reviewTaskFileMapper.insert(tf);
        }
    }

    /** 字节写临时文件（保留真实文件名以便 OSS 取到正确后缀）→ 上传，入当前租户 */
    private SysOssVo uploadBytes(byte[] bytes, String fileName) {
        Path dir = null;
        File f = null;
        try {
            String safe = safeName(fileName);
            if (!safe.contains(".")) {
                safe = safe + ".bin";
            }
            dir = Files.createTempDirectory("review_imp_");
            f = dir.resolve(safe).toFile();
            Files.write(f.toPath(), bytes);
            return ossService.upload(f);
        } catch (Exception e) {
            log.warn("[ReviewProject] 附件重传失败 name={}: {}", fileName, e.getMessage());
            return null;
        } finally {
            try {
                if (f != null) { Files.deleteIfExists(f.toPath()); }
                if (dir != null) { Files.deleteIfExists(dir); }
            } catch (Exception ignore) { }
        }
    }

    private String uploadSearchable(byte[] bytes) {
        try {
            UploadResult up = OssFactory.instance().uploadSuffix(bytes, ".pdf", "application/pdf");
            return up.getUrl();
        } catch (Exception e) {
            log.warn("[ReviewProject] searchable 重传失败: {}", e.getMessage());
            return null;
        }
    }

    /** 解 zip 到内存：zip-slip 校验 + 总量上限 */
    private Map<String, byte[]> readZip(MultipartFile file) {
        Map<String, byte[]> map = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName().replace('\\', '/');
                if (name.contains("../") || name.startsWith("/") || name.contains(":")) {
                    throw new ServiceException("非法的压缩包条目：" + name);
                }
                if (e.isDirectory()) {
                    continue;
                }
                byte[] data = zis.readAllBytes();
                total += data.length;
                if (total > MAX_TOTAL_BYTES) {
                    throw new ServiceException("工程包解压后体积超过上限");
                }
                map.put(name, data);
            }
        } catch (ServiceException se) {
            throw se;
        } catch (Exception ex) {
            throw new ServiceException("读取工程包失败：" + ex.getMessage());
        }
        return map;
    }

    /** 文件名消毒：去路径分隔与控制字符，保留中文与常见符号 */
    private String safeName(String name) {
        if (StringUtils.isBlank(name)) {
            return "file";
        }
        String n = name.replace('\\', '/');
        int slash = n.lastIndexOf('/');
        if (slash >= 0) {
            n = n.substring(slash + 1);
        }
        n = n.replaceAll("[\\x00-\\x1f<>:\"|?*]", "_").trim();
        return n.isEmpty() ? "file" : n;
    }
}
