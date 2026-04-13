package org.example.springbootdemo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.springbootdemo.dto.DeptDTO;
import org.example.springbootdemo.query.DeptQuery;
import java.util.List;

@Mapper
public interface DeptMapper {

    /**
     * 通过ID查询单条数据
     *
     * @param id 主键
     * @return 实例对象
     */
    DeptDTO queryById(Long id);

    /**
     * 分页查询部门列表
     *
     * @param deptQuery 查询条件（包含分页参数）
     * @return 对象列表
     */
    List<DeptDTO> find(DeptQuery deptQuery);

    /**
     * 新增数据
     *
     * @param dept 实例对象
     * @return 影响行数
     */
    int insert(DeptDTO dept);


    /**
     * 修改数据
     *
     * @param dept 实例对象
     * @return 影响行数
     */
    int update(DeptDTO dept);

    /**
     * 通过主键删除数据
     *
     * @param id 主键
     * @return 影响行数
     */
    int deleteById(Long id);

}

