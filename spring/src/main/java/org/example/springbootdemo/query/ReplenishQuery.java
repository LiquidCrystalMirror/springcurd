package org.example.springbootdemo.query;

import lombok.Data;
import org.example.springbootdemo.query.base.BaseQuery;

/**
 * 补货单查询条件
 */
@Data
public class ReplenishQuery extends BaseQuery {
    /** 状态筛选：null=全部, 0=待审核, 1=审核通过, 2=审核拒绝, 3=已执行 */
    private Integer status;
}
