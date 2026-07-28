// vo/ReplenishVO.java
package org.example.springbootdemo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.util.Map;

@Data
public class ReplenishVO {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;  // 后端生成的雪花批次ID（序列化为字符串避免 JS 精度丢失）
    private Map<Long, Integer> operations;
}