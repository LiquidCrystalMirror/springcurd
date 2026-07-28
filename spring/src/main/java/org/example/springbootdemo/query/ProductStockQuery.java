package org.example.springbootdemo.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.example.springbootdemo.query.base.BaseQuery;

@Data
public class ProductStockQuery extends BaseQuery {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long productId;
    private Integer stock;
}
