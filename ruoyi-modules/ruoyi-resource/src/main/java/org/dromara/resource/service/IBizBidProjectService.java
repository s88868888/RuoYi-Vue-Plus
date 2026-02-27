package org.dromara.resource.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.resource.domain.BizBidProject;
import org.dromara.resource.domain.bo.BizBidProjectBo;
import org.dromara.resource.domain.dto.QuickGenerateDto;
import org.dromara.resource.domain.vo.BizBidProjectVo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 招标项目Service接口
 *
 * @author ruoyi
 * @date 2026-02-23
 */
public interface IBizBidProjectService extends IService<BizBidProject> {

    /**
     * 查询招标项目分页列表
     *
     * @param bo       查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<BizBidProjectVo> queryPageList(BizBidProjectBo bo, PageQuery pageQuery);

    /**
     * 查询招标项目列表
     *
     * @param bo 查询条件
     * @return 列表
     */
    List<BizBidProjectVo> queryList(BizBidProjectBo bo);

    /**
     * 根据ID查询招标项目
     *
     * @param id 主键ID
     * @return 招标项目
     */
    BizBidProjectVo queryById(Long id);

    /**
     * 新增招标项目
     *
     * @param bo 业务对象
     * @return 结果
     */
    Boolean insertByBo(BizBidProjectBo bo);

    /**
     * 修改招标项目
     *
     * @param bo 业务对象
     * @return 结果
     */
    Boolean updateByBo(BizBidProjectBo bo);

    /**
     * 删除招标项目
     *
     * @param ids 主键ID列表
     * @param isValid 是否校验
     * @return 结果
     */
    Boolean deleteWithValidByIds(List<Long> ids, Boolean isValid);

    /**
     * 从PDF文件快速生成招标项目
     *
     * @param dto  快速生成DTO
     * @param file PDF文件
     * @return 招标项目ID
     */
    Long quickGenerateFromPdf(QuickGenerateDto dto, MultipartFile file);

}
