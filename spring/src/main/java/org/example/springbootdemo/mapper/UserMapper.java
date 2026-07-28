package org.example.springbootdemo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.springbootdemo.dto.UserDTO;
import org.example.springbootdemo.query.UserQuery;
import org.example.springbootdemo.vo.UserDetailVO;
import org.example.springbootdemo.vo.UserVO;

import java.util.List;

@Mapper
public interface UserMapper {
    List<UserVO> getUserList();
    UserVO getUserById(int id);

    /** 获取用户详情（含创建人/审核人/时间戳） */
    UserDetailVO getUserDetailById(int id);
    int insertUser(UserDTO userDTO);

    /** 人事创建用户（status=2待审核, 写入creator_id） */
    int insertUserWithAudit(@Param("id") Integer id,
                            @Param("name") String name,
                            @Param("password") String password,
                            @Param("roleId") Byte roleId,
                            @Param("creatorId") Integer creatorId);

    /** 监管审核用户（更新status/approver_id/approve_time） */
    int approveUser(@Param("id") Integer id,
                    @Param("status") Byte status,
                    @Param("approverId") Integer approverId,
                    @Param("remark") String remark);

    int updateUser(UserDTO userVO);
    int deleteUser(int id);
    String getPassword(int id);
    List<UserVO> find(UserQuery userQuery);
}
