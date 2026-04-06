package org.example.springbootdemo.query;

import lombok.Data;
import org.example.springbootdemo.query.base.BaseQuery;
@Data
public class UserQuery extends BaseQuery {
    private String name;
    private byte[] status;
    private byte[] roleId;
}
