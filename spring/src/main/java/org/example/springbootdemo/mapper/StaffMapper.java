package org.example.springbootdemo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.springbootdemo.dto.StaffDTO;
import org.example.springbootdemo.query.UserQuery;
import org.example.springbootdemo.vo.UserDetailVO;
import org.example.springbootdemo.vo.UserVO;

import java.util.List;

/**
 * 内部员工 Mapper（补货员/人事/监管/管理员）
 */
@Mapper
public interface StaffMapper {

    /** 员工列表（分页查询用） */
    List<UserVO> find(UserQuery query);

    /** 根据ID查询 */
    UserVO getById(int id);

    /** 员工详情（含审计字段） */
    UserDetailVO getDetailById(int id);

    /** 人事创建员工（status=2待审核, 写入creator_id） */
    int insertWithAudit(@Param("id") Integer id,
                        @Param("name") String name,
                        @Param("password") String password,
                        @Param("roleId") Byte roleId,
                        @Param("creatorId") Integer creatorId);

    /** 监管审核员工（status=1通过/3拒绝，写入approver_id） */
    int approve(@Param("id") Integer id,
                @Param("status") Byte status,
                @Param("approverId") Integer approverId);

    /** 更新员工信息 */
    int update(StaffDTO dto);

    /** 删除员工 */
    int delete(int id);

    /** 获取密码（登录校验） */
    String getPassword(int id);
}
