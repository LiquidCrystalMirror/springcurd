// vo/ReplenishVO.java
package org.example.springbootdemo.vo;

import lombok.Data;
import java.util.Map;

@Data
public class ReplenishVO {
    private Long id;  // 后端生成的雪花批次ID
    private Map<Long, Integer> operations;
}