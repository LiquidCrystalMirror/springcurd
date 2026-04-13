package org.example.springbootdemo.service.imp;


import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import org.example.springbootdemo.dto.DeptDTO;
import org.example.springbootdemo.mapper.DeptMapper;
import org.example.springbootdemo.query.DeptQuery;
import org.example.springbootdemo.service.DeptService;
import org.springframework.stereotype.Service;

@Service
public class DeptServiceImpl implements DeptService {
    @Resource
    private DeptMapper deptMapper;
    @Override
    public DeptDTO queryById(Long id) { return deptMapper.queryById(id);}
    @Override
    public PageInfo<DeptDTO> find(DeptQuery deptQuery) {
        PageHelper.startPage(deptQuery.getPage(),deptQuery.getPageSize());
        Page<DeptDTO> list = (Page<DeptDTO>)deptMapper.find(deptQuery);
        PageInfo<DeptDTO> pageInfo =  list.toPageInfo();
        return pageInfo;
    }
    @Override
    public int insert(DeptDTO deptDTO) { return deptMapper.insert(deptDTO);}
    @Override
    public int update(DeptDTO deptDTO) { return deptMapper.update(deptDTO);}
    @Override
    public int deleteById(Long id) { return deptMapper.deleteById(id);}
}