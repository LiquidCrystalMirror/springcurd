package org.example.springbootdemo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.springbootdemo.dto.UserDTO;
import org.example.springbootdemo.vo.UserVO;

import java.util.List;

@Mapper
public interface UserMapper {
    List<UserVO> getUserList();
    UserVO getUserById(int id);
    int insertUser(UserDTO userDTO);
    int updateUser(UserVO userVO);
    int deleteUser(int id);
    String getPassword(int id);
}
