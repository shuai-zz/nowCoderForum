package org.example.nowcoder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.nowcoder.entity.User;

/**
 * @author 23211
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("select * from user where username=#{username}")
    User selectByName(String username);

    @Select("select * from user where email=#{email}")
    User selectByEmail(String email);

    default int updateStatus(int id, int status) {
        return update(null, Wrappers.<User>lambdaUpdate()
                .set(User::getStatus, status)
                .eq(User::getId, id));
    }

    default int updateAvatar(int id, String avatarUrl) {
        return update(null, Wrappers.<User>lambdaUpdate()
                .set(User::getAvatarUrl, avatarUrl)
                .eq(User::getId, id));
    }

    default int updatePassword(int id, String password) {
        return update(null, Wrappers.<User>lambdaUpdate()
                .set(User::getPassword, password)
                .eq(User::getId, id));
    }
}