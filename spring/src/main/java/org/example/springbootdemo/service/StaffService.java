package org.example.springbootdemo.service;

import com.github.pagehelper.PageInfo;
import org.example.springbootdemo.dto.StaffDTO;
import org.example.springbootdemo.query.UserQuery;
import org.example.springbootdemo.vo.UserDetailVO;
import org.example.springbootdemo.vo.UserVO;

import java.util.List;

/**
 * 内部员工 Service（补货员/人事/监管/管理员）
 */
public interface StaffService {
    /** 员工列表 */
    List<UserVO> getList();

    /** 分页查询 */
    PageInfo<UserVO> find(UserQuery query);

    /** 单个查询 */
    UserVO getById(int id);

    /** 员工详情 */
    UserDetailVO getDetailById(int id);

    /** 人事创建员工 */
    int create(StaffDTO dto, Integer creatorId);

    /** 监管审核员工（通过/拒绝） */
    int approve(Integer id, boolean approved, Integer approverId);

    /** 更新员工 */
    int update(StaffDTO dto);

    /** 删除员工 */
    int delete(int id);

    /** 校验密码 */
    boolean checkPassword(int id, String password);
}
