package org.example.springbootdemo.query;

import lombok.Data;
import org.example.springbootdemo.query.base.BaseQuery;

@Data
public class ProductStockQuery extends BaseQuery {
    private Long productId;
    private Integer stock;
}
