package org.dromara.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.ai.service.CompanyVectorService;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.entity.UploadResult;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.resource.domain.BizProjectKnowledge;
import org.dromara.resource.domain.bo.BizProjectKnowledgeBo;
import org.dromara.resource.domain.vo.BizProjectKnowledgeVo;
import org.dromara.resource.mapper.BizProjectKnowledgeMapper;
import org.dromara.resource.service.DocumentParserService;
import org.dromara.resource.service.IBizProjectKnowledgeService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目知识Service业务层处理
 *
 * @author ruoyi
 * @date 2026-02-12
 */
@RequiredArgsConstructor
@Service
public class BizProjectKnowledgeServiceImpl implements IBizProjectKnowledgeService {

    private final BizProjectKnowledgeMapper baseMapper;
    private final DocumentParserService documentParserService;
    private final CompanyVectorService companyVectorService;
    private final OssClient ossClient;

    /**
     * 查询项目知识
     */
    @Override
    public BizProjectKnowledgeVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 查询项目知识列表
     */
    @Override
    public TableDataInfo<BizProjectKnowledgeVo> queryPageList(BizProjectKnowledgeBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BizProjectKnowledge> lqw = buildQueryWrapper(bo);
        Page<BizProjectKnowledgeVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询项目知识列表
     */
    @Override
    public List<BizProjectKnowledgeVo> queryList(BizProjectKnowledgeBo bo) {
        LambdaQueryWrapper<BizProjectKnowledge> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<BizProjectKnowledge> buildQueryWrapper(BizProjectKnowledgeBo bo) {
        LambdaQueryWrapper<BizProjectKnowledge> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getDeptId() != null, BizProjectKnowledge::getDeptId, bo.getDeptId());
        lqw.like(StringUtils.isNotBlank(bo.getKnowledgeName()), BizProjectKnowledge::getKnowledgeName, bo.getKnowledgeName());
        lqw.eq(StringUtils.isNotBlank(bo.getProjectType()), BizProjectKnowledge::getProjectType, bo.getProjectType());
        lqw.like(StringUtils.isNotBlank(bo.getDescription()), BizProjectKnowledge::getDescription, bo.getDescription());
        lqw.orderByDesc(BizProjectKnowledge::getCreateTime);
        return lqw;
    }

    /**
     * 新增项目知识
     */
    @Override
    public Boolean insertByBo(BizProjectKnowledgeBo bo) {
        BizProjectKnowledge add = MapstructUtils.convert(bo, BizProjectKnowledge.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改项目知识
     */
    @Override
    public Boolean updateByBo(BizProjectKnowledgeBo bo) {
        BizProjectKnowledge update = MapstructUtils.convert(bo, BizProjectKnowledge.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(BizProjectKnowledge entity) {
        // TODO 做一些数据校验,如唯一约束
    }

    /**
     * 批量删除项目知识
     */
    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (isValid) {
            // TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteByIds(ids) > 0;
    }

    /**
     * 上传项目知识文档并向量化存储
     */
    @Override
    public Boolean uploadDocument(BizProjectKnowledgeBo bo, MultipartFile file) {
        try {
            // 1. 验证文件类型
            String filename = file.getOriginalFilename();
            if (!documentParserService.isValidFileType(filename)) {
                throw new IllegalArgumentException("仅支持 PDF 和 Word 文档格式");
            }

            // 2. 上传文件到 OSS
            String suffix = filename.substring(filename.lastIndexOf("."));
            UploadResult uploadResult = ossClient.uploadSuffix(file.getInputStream(), suffix, file.getSize(), file.getContentType());
            bo.setAttachmentUrl(uploadResult.getUrl());
            bo.setAttachmentName(filename);

            // 3. 解析文档内容
            String content = documentParserService.parseDocument(file);
            bo.setDescription(content.length() > 500 ? content.substring(0, 500) + "..." : content);

            // 4. 如果没有设置知识名称，从文件名提取
            if (StringUtils.isBlank(bo.getKnowledgeName())) {
                String knowledgeName = filename.substring(0, filename.lastIndexOf("."));
                bo.setKnowledgeName(knowledgeName);
            }

            // 5. 保存到数据库
            BizProjectKnowledge entity = MapstructUtils.convert(bo, BizProjectKnowledge.class);
            validEntityBeforeSave(entity);
            boolean saved = baseMapper.insert(entity) > 0;

            if (!saved) {
                return false;
            }

            // 6. 向量化存储到 Milvus
            String tenantId = TenantHelper.getTenantId();
            Long companyId = bo.getDeptId(); // 使用部门ID作为公司ID

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("id", entity.getId());
            metadata.put("knowledgeName", bo.getKnowledgeName());
            metadata.put("projectType", bo.getProjectType());
            metadata.put("attachmentUrl", uploadResult.getUrl());
            metadata.put("attachmentName", filename);
            metadata.put("title", bo.getKnowledgeName());
            metadata.put("content", content);

            companyVectorService.addProjectKnowledge(tenantId, companyId, metadata);

            bo.setId(entity.getId());
            return true;

        } catch (Exception e) {
            throw new RuntimeException("上传项目知识文档失败: " + e.getMessage(), e);
        }
    }
}
