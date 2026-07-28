package org.example.springbootdemo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.springbootdemo.dto.CustomerDTO;
import org.example.springbootdemo.vo.UserVO;

import java.util.List;

/**
 * 购买用户 Mapper（C端消费者）
 */
@Mapper
public interface CustomerMapper {

    /** 注册新购买用户 */
    int insert(CustomerDTO dto);

    /** 根据ID查询 */
    UserVO getById(int id);

    /** 获取密码（登录校验） */
    String getPassword(int id);

    /** 更新用户信息 */
    int update(CustomerDTO dto);

    /** 禁用/启用 */
    int updateStatus(@Param("id") int id, @Param("status") Byte status);
}
