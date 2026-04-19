package org.example.springbootdemo.service;

import org.example.springbootdemo.dto.ApiResult;
import org.example.springbootdemo.dto.ReplenishDTO;
import org.example.springbootdemo.vo.ReplenishVO;

public interface ReplenishService {
    ApiResult<ReplenishVO> replenish(ReplenishDTO dto);
}