package com.example.user.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author 23211
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("select * from user where username=#{username}")
    User selectByName(String username);

    @Select("select * from user where email=#{email}")
    User selectByEmail(String email);


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


    default boolean existsUsername(String username){
        return exists(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username));
    }

    default boolean existsEmail(String email){
        return exists(Wrappers.<User>lambdaQuery()
                .eq(User::getEmail, email));
    }

    default List<User> selectBatchIds(List<Integer> ids){
        return selectList(Wrappers.<User>lambdaQuery()
                .in(User::getId, ids));
    }

}