package org.dromara.resource.service;

import jakarta.servlet.http.HttpServletResponse;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.resource.domain.bo.BizBidSubmissionBo;
import org.dromara.resource.domain.vo.BizBidSubmissionVo;
import org.dromara.resource.domain.vo.BidSubmissionProgressVo;
import org.dromara.resource.domain.vo.BizSubmissionChapterVo;

import java.util.Collection;
import java.util.List;

/**
 * 投标项目Service接口
 *
 * @author ruoyi
 * @date 2026-02-26
 */
public interface IBizBidSubmissionService {

    /**
     * 查询投标项目
     */
    BizBidSubmissionVo queryById(Long id);

    /**
     * 查询投标项目列表
     */
    TableDataInfo<BizBidSubmissionVo> queryPageList(BizBidSubmissionBo bo, PageQuery pageQuery);

    /**
     * 查询投标项目列表用于导出
     */
    List<BizBidSubmissionVo> queryList(BizBidSubmissionBo bo);

    /**
     * 新增投标项目
     */
    Boolean insertByBo(BizBidSubmissionBo bo);

    /**
     * 修改投标项目
     */
    Boolean updateByBo(BizBidSubmissionBo bo);

    /**
     * 校验并批量删除投标项目
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    /**
     * 从招标项目创建投标项目
     *
     * @param bidProjectId 招标项目ID
     * @param bo 投标项目业务对象
     * @return 投标项目ID
     */
    Long createFromBidProject(Long bidProjectId, BizBidSubmissionBo bo);

    /**
     * 开始生成标书
     *
     * @param submissionId 投标项目ID
     * @return 是否成功
     */
    Boolean startGeneration(Long submissionId);

    /**
     * 获取生成进度
     *
     * @param submissionId 投标项目ID
     * @return 进度信息
     */
    BidSubmissionProgressVo getProgress(Long submissionId);

    /**
     * 取消生成
     *
     * @param submissionId 投标项目ID
     * @return 是否成功
     */
    Boolean cancelGeneration(Long submissionId);

    /**
     * 重新生成
     *
     * @param submissionId 投标项目ID
     * @return 是否成功
     */
    Boolean regenerate(Long submissionId);

    /**
     * 第一步：保存公司关联和生成配置
     *
     * @param bo 配置信息
     * @return 是否成功
     */
    Boolean saveStep1Config(BizBidSubmissionBo bo);

    /**
     * 第二步：生成章节结构
     *
     * @param submissionId 投标项目ID
     * @return 是否成功
     */
    Boolean generateChapterStructure(Long submissionId);

    /**
     * 第二步：获取章节树结构
     *
     * @param submissionId 投标项目ID
     * @return 章节树
     */
    List<BizSubmissionChapterVo> getChapterTree(Long submissionId);

    /**
     * 第二步：开始生成标书内容
     *
     * @param submissionId 投标项目ID
     * @return 是否成功
     */
    Boolean startContentGeneration(Long submissionId);

    /**
     * 第三步：导出标书文件
     *
     * @param submissionId 投标项目ID
     * @param response HTTP响应
     */
    void exportDocument(Long submissionId, HttpServletResponse response);

}
