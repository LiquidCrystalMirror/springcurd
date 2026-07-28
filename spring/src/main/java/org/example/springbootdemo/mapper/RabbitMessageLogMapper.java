package org.example.springbootdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.springbootdemo.entity.RabbitMessageLog;

/**
 * 本地消息表 Mapper
 */
@Mapper
public interface RabbitMessageLogMapper extends BaseMapper<RabbitMessageLog> {

    /**
     * 更新消息状态为成功
     */
    int updateSuccess(@Param("messageId") String messageId,
                      @Param("status") Integer status);

    /**
     * 更新消息状态为失败
     */
    int updateFailed(@Param("messageId") String messageId,
                     @Param("status") Integer status,
                     @Param("errorMsg") String errorMsg,
                     @Param("retryCount") Integer retryCount);
}
