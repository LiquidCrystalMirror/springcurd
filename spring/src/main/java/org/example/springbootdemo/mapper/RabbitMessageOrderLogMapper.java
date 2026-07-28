package org.example.springbootdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.springbootdemo.entity.RabbitMessageOrderLog;

/**
 * 订单消息日志表 Mapper（高流量独立表）
 */
@Mapper
public interface RabbitMessageOrderLogMapper extends BaseMapper<RabbitMessageOrderLog> {

    /** 更新消息状态为成功（必须原状态为 0 待发送才能更新，防止并发重复） */
    int updateSuccess(@Param("messageId") String messageId,
                      @Param("status") Integer status);

    /** 更新消息状态为失败，同时记录错误信息和重试次数 */
    int updateFailed(@Param("messageId") String messageId,
                     @Param("status") Integer status,
                     @Param("errorMsg") String errorMsg,
                     @Param("retryCount") Integer retryCount);
}
