package org.dromara.resource.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.resource.domain.bo.BizBidSubmissionBo;
import org.dromara.resource.domain.vo.BizBidSubmissionVo;
import org.dromara.resource.domain.vo.BidSubmissionProgressVo;

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

}
