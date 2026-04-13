package org.example.springbootdemo.query;

import lombok.Data;
import org.example.springbootdemo.query.base.BaseQuery;

@Data
public class DeptQuery extends BaseQuery {
    private String name;
}
