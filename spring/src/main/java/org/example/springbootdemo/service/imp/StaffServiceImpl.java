package org.example.springbootdemo.service.imp;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import org.example.springbootdemo.dto.StaffDTO;
import org.example.springbootdemo.mapper.StaffMapper;
import org.example.springbootdemo.query.UserQuery;
import org.example.springbootdemo.service.StaffService;
import org.example.springbootdemo.util.PasswordUtil;
import org.example.springbootdemo.vo.UserDetailVO;
import org.example.springbootdemo.vo.UserVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StaffServiceImpl implements StaffService {

    @Resource
    private StaffMapper staffMapper;
    @Resource
    private PasswordUtil passwordUtil;

    @Override
    public List<UserVO> getList() {
        return staffMapper.find(new UserQuery());
    }

    @Override
    public PageInfo<UserVO> find(UserQuery query) {
        PageHelper.startPage(query.getPage(), query.getPageSize());
        Page<UserVO> list = (Page<UserVO>) staffMapper.find(query);
        return list.toPageInfo();
    }

    @Override
    public UserVO getById(int id) {
        return staffMapper.getById(id);
    }

    @Override
    public UserDetailVO getDetailById(int id) {
        return staffMapper.getDetailById(id);
    }

    @Override
    public int create(StaffDTO dto, Integer creatorId) {
        dto.setPassword(passwordUtil.encode(dto.getPassword()));
        return staffMapper.insertWithAudit(
                dto.getId(), dto.getName(), dto.getPassword(), dto.getRoleId(), creatorId);
    }

    @Override
    public int approve(Integer id, boolean approved, Integer approverId) {
        byte status = approved ? (byte) 1 : (byte) 3;
        return staffMapper.approve(id, status, approverId);
    }

    @Override
    public int update(StaffDTO dto) {
        return staffMapper.update(dto);
    }

    @Override
    public int delete(int id) {
        return staffMapper.delete(id);
    }

    @Override
    public boolean checkPassword(int id, String password) {
        String pwd = staffMapper.getPassword(id);
        return passwordUtil.verifyPassword(password, pwd);
    }
}
