package org.example.springbootdemo.service.imp;

import lombok.Data;
import org.example.springbootdemo.dto.UserDTO;
import org.example.springbootdemo.mapper.UserMapper;
import org.example.springbootdemo.service.UserService;
import org.example.springbootdemo.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.util.List;

@Service
@Data
public class UserServiceImp implements UserService {
    @Autowired
    private UserMapper userMapper;
    @Override
    public List<UserVO> getUserList() {
        return userMapper.getUserList();
    }

    @Override
    public UserVO getUserById(int id) {
        return userMapper.getUserById(id);
    }

    @Override
    public int addUser(UserDTO userDTO) {
        return userMapper.insertUser(userDTO);
    }

    @Override
    public int updateUser(UserVO userVO) {
        return userMapper.updateUser(userVO);
    }

    @Override
    public int deleteUser(int id) {
        return userMapper.deleteUser(id);
    }

    @Override
    public boolean checkPassword(int id, String password) {
        String pwd = userMapper.getPassword(id);
        return pwd.equals(password);
    }
}
