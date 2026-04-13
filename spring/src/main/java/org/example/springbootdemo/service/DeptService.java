package org.example.springbootdemo.service;

import com.github.pagehelper.PageInfo;
import org.example.springbootdemo.dto.DeptDTO;
import org.example.springbootdemo.query.DeptQuery;

public interface DeptService {
    DeptDTO queryById(Long id);

    PageInfo<DeptDTO> find(DeptQuery deptQuery);

    int insert(DeptDTO deptDTO);

    int update(DeptDTO deptDTO);

    int deleteById(Long id);
}
