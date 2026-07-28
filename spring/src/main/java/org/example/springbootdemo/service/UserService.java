package org.example.springbootdemo.service;

import com.github.pagehelper.PageInfo;
import org.example.springbootdemo.dto.UserCreateDTO;
import org.example.springbootdemo.dto.UserApproveDTO;
import org.example.springbootdemo.dto.UserDTO;
import org.example.springbootdemo.query.UserQuery;
import org.example.springbootdemo.vo.UserDetailVO;
import org.example.springbootdemo.vo.UserVO;

import java.util.List;

public interface UserService {
    List<UserVO> getUserList();
    UserVO getUserById(int id);
    UserDetailVO getUserDetailById(int id);

    /** 人事创建用户（status=2待审核, 记录creator_id） */
    int createUser(UserCreateDTO dto, Integer creatorId);

    /** 监管审核用户（通过/拒绝） */
    int approveUser(UserApproveDTO dto, Integer approverId);

    int addUser(UserDTO userDTO);
    int updateUser(UserDTO userDTO);
    int deleteUser(int id);
    boolean checkPassword(int id,String password);
    PageInfo<UserVO> find(UserQuery userQuery);
}
