package org.example.springbootdemo.query;

import lombok.Data;
import org.example.springbootdemo.query.base.BaseQuery;

@Data
public class OrderQuery extends BaseQuery {
    private String orderNo;
    private String platformId;
    private Long productId;
    private Integer quantity;
    private Byte status;
}
