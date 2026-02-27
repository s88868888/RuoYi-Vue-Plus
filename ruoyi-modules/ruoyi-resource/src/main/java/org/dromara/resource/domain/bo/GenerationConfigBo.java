package org.dromara.resource.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 生成配置项业务对象
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Data
public class GenerationConfigBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 公司ID
     */
    private Long companyId;

    /**
     * 公司名称
     */
    private String companyName;

    /**
     * 商务标数量
     */
    private Integer commercial;

    /**
     * 技术标数量
     */
    private Integer technical;

    /**
     * 整本标书数量
     */
    private Integer complete;

}
