package org.dromara.review.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.review.domain.bo.ReviewTaskBo;
import org.dromara.review.domain.vo.ReviewResultItemVo;
import org.dromara.review.domain.vo.ReviewTaskVo;

import java.util.Collection;
import java.util.List;

/**
 * 审核任务Service接口
 *
 * @author ruoyi
 * @date 2026-05-09
 */
public interface IReviewTaskService {

    /**
     * 查询审核任务详情（附带文件列表和标准名称）
     */
    ReviewTaskVo queryById(Long id);

    /**
     * 查询审核任务分页列表
     */
    TableDataInfo<ReviewTaskVo> queryPageList(ReviewTaskBo bo, PageQuery pageQuery);

    /**
     * 创建审核任务（含关联标准、保存附件）
     */
    Long createTask(ReviewTaskBo bo);

    /**
     * 修改审核任务
     */
    Boolean updateByBo(ReviewTaskBo bo);

    /**
     * 校验并批量删除审核任务信息
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    /**
     * 触发AI审核
     */
    void executeReview(Long taskId);

    /**
     * 标记误判
     */
    void markMisjudgment(Long resultItemId, String reason);

    /**
     * 查询任务的审核结果明细列表
     */
    List<ReviewResultItemVo> queryResultItems(Long taskId);

}
