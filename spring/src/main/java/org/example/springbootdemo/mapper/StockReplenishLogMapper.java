package org.example.springbootdemo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.springbootdemo.entity.StockReplenishLog;
import java.util.List;

@Mapper
public interface StockReplenishLogMapper {
    int batchInsert(@Param("list") List<StockReplenishLog> logs);
}