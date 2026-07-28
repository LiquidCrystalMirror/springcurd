package org.example.springbootdemo.service;

import org.example.springbootdemo.dto.CustomerDTO;
import org.example.springbootdemo.vo.UserVO;

/**
 * 购买用户 Service（C端消费者）
 */
public interface CustomerService {
    /** 注册 */
    int register(CustomerDTO dto);

    /** 查询 */
    UserVO getById(int id);

    /** 校验密码 */
    boolean checkPassword(int id, String password);

    /** 更新信息 */
    int update(CustomerDTO dto);
}
