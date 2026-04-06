package org.example.springbootdemo.service;

import com.github.pagehelper.PageInfo;
import org.example.springbootdemo.dto.UserDTO;
import org.example.springbootdemo.query.UserQuery;
import org.example.springbootdemo.vo.UserVO;

import java.util.List;

public interface UserService {
    List<UserVO> getUserList();
    UserVO getUserById(int id);
    int addUser(UserDTO userDTO);
    int updateUser(UserDTO userDTO);
    int deleteUser(int id);
    boolean checkPassword(int id,String password);
    PageInfo<UserVO> find(UserQuery userQuery);
}
