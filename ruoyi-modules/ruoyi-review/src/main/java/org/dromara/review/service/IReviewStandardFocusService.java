package org.dromara.review.service;

import org.dromara.review.domain.bo.ReviewStandardFocusBo;
import org.dromara.review.domain.vo.ReviewStandardFocusVo;

import java.util.Collection;
import java.util.List;

/**
 * 审核标准关注要点Service接口
 *
 * @author ruoyi
 * @date 2026-06-17
 */
public interface IReviewStandardFocusService {

    /**
     * 按标准ID查询关注要点列表（按排序）
     */
    List<ReviewStandardFocusVo> queryListByStandardId(Long standardId);

    /**
     * 新增关注要点
     */
    Boolean insertByBo(ReviewStandardFocusBo bo);

    /**
     * 修改关注要点
     */
    Boolean updateByBo(ReviewStandardFocusBo bo);

    /**
     * 批量删除关注要点
     */
    Boolean deleteByIds(Collection<Long> ids);

}
