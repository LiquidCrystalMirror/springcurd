package org.example.springbootdemo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 业务幂等性记录Mapper
 */
@Mapper
public interface BizIdempotentMapper {

    /**
     * 插入幂等记录
     * @param bizNo 业务单号
     * @param opType 操作类型
     * @param platformId 平台ID
     * @param status 状态
     * @return 影响行数
     */
    int insert(@Param("bizNo") String bizNo, 
               @Param("opType") String opType, 
               @Param("platformId") String platformId, 
               @Param("status") Integer status);

    /**
     * 查询幂等记录
     * @param bizNo 业务单号
     * @param opType 操作类型
     * @param platformId 平台ID
     * @return 状态值，不存在返回null
     */
    Integer selectStatus(@Param("bizNo") String bizNo, 
                         @Param("opType") String opType, 
                         @Param("platformId") String platformId);

    /**
     * 更新幂等记录状态
     * @param bizNo 业务单号
     * @param opType 操作类型
     * @param platformId 平台ID
     * @param status 新状态
     * @return 影响行数
     */
    int updateStatus(@Param("bizNo") String bizNo, 
                     @Param("opType") String opType, 
                     @Param("platformId") String platformId, 
                     @Param("status") Integer status);
}
