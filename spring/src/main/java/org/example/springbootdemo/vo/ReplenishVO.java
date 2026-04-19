// vo/ReplenishVO.java
package org.example.springbootdemo.vo;

import lombok.Data;
import java.util.Map;

@Data
public class ReplenishVO {
    private String replenishNo;
    private Map<Long, Integer> operations;
}