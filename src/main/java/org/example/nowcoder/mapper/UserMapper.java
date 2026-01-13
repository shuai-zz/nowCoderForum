package org.example.nowcoder.mapper;

import org.apache.ibatis.annotations.*;
import org.example.nowcoder.entity.User;

/**
 * @author 23211
 */
@Mapper
public interface UserMapper {
    @Select("select * from user where id=#{id}")
    User selectById(int id);

    @Select("select * from user where username=#{username}")
    User selectByName(String username);

    @Select("select * from user where email= #{email}")
    User selectByEmail(String email);


    @Insert("insert into user(username,password,salt,email,type,status,activation_code,avatar_url,create_time) " +
            "values(#{username},#{password},#{salt},#{email},#{type},#{status},#{activationCode},#{avatarUrl},#{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUser(User user);

    @Update("update user set status=#{status} where id=#{id}")
    int updateStatus(int id, int status);

    @Update("update user set avatar_url= #{avatarUrl} where id= #{id}")
    int updateAvatar(int id, String avatarUrl);

    @Update("update user set password= #{password} where id= #{id}")
    int updatePassword(int id, String password);
}
