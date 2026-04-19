package org.example.springbootdemo.vo;

import lombok.Data;

import java.util.Map;
@Data
public class OrderVO {
    private String orderNo;
    private String platformId;
    private Map<Long, Integer> operations;
}
