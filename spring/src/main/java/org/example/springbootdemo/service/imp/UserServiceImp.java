package org.example.springbootdemo.service.imp;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import lombok.Data;
import org.example.springbootdemo.dto.UserDTO;
import org.example.springbootdemo.mapper.UserMapper;
import org.example.springbootdemo.query.UserQuery;
import org.example.springbootdemo.service.UserService;
import org.example.springbootdemo.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.example.springbootdemo.util.PasswordUtil;

import java.util.List;

@Service
@Data
public class UserServiceImp implements UserService {
    @Resource
    private UserMapper userMapper;
    @Resource
    private PasswordUtil passwordUtil;
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
    public int updateUser(UserDTO userDTO) {
        return userMapper.updateUser(userDTO);
    }

    @Override
    public int deleteUser(int id) {
        return userMapper.deleteUser(id);
    }

    @Override
    public boolean checkPassword(int id, String password) {
        String pwd = userMapper.getPassword(id);
        return passwordUtil.verifyPassword(password, pwd);
    }
    @Override
    public PageInfo<UserVO> find(UserQuery userQuery) {
        PageHelper.startPage(userQuery.getPage(),userQuery.getPageSize());
        Page<UserVO> list = (Page<UserVO>)userMapper.find(userQuery);
        PageInfo<UserVO> pageInfo =  list.toPageInfo();
        return pageInfo;
    }
}
