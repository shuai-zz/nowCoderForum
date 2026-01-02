package org.example.nowcoder.service.impl;

import com.github.pagehelper.PageHelper;
import lombok.RequiredArgsConstructor;
import org.example.nowcoder.entity.DiscussPost;
import org.example.nowcoder.entity.User;
import org.example.nowcoder.mapper.UserMapper;
import org.example.nowcoder.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author 23211
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;


    @Override
    public User findUserById(int id) {
        return userMapper.selectById(id);
    }
}
