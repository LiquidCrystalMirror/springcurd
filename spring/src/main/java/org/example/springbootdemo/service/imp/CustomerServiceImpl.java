package org.example.springbootdemo.service.imp;

import jakarta.annotation.Resource;
import org.example.springbootdemo.dto.CustomerDTO;
import org.example.springbootdemo.mapper.CustomerMapper;
import org.example.springbootdemo.service.CustomerService;
import org.example.springbootdemo.util.PasswordUtil;
import org.example.springbootdemo.vo.UserVO;
import org.springframework.stereotype.Service;

@Service
public class CustomerServiceImpl implements CustomerService {

    @Resource
    private CustomerMapper customerMapper;
    @Resource
    private PasswordUtil passwordUtil;

    @Override
    public int register(CustomerDTO dto) {
        dto.setPassword(passwordUtil.encode(dto.getPassword()));
        return customerMapper.insert(dto);
    }

    @Override
    public UserVO getById(int id) {
        return customerMapper.getById(id);
    }

    @Override
    public boolean checkPassword(int id, String password) {
        String pwd = customerMapper.getPassword(id);
        return passwordUtil.verifyPassword(password, pwd);
    }

    @Override
    public int update(CustomerDTO dto) {
        return customerMapper.update(dto);
    }
}
