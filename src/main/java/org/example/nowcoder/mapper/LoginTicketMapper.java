package org.example.nowcoder.mapper;

import org.apache.ibatis.annotations.*;
import org.example.nowcoder.entity.LoginTicket;

/**
 * @author 23211
 */
@Mapper
public interface LoginTicketMapper {

    @Insert("insert into login_ticket(user_id, ticket, status, expired) values (#{userId}, #{ticket}, #{status}, #{expired})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertLoginTicket(LoginTicket loginTicket);

    @Select("select id, user_id, ticket, status, expired from login_ticket where ticket=#{ticket}")
    LoginTicket selectByTicket(String ticket);

    @Update({
            "<script>",
            "update login_ticket set status=#{status} where ticket=#{ticket} ",
            "<if test=\"ticket!=null\"> ",
            "and 1=1 ",
            "</if>",
            "</script>"
    })
    int updateStatus(String ticket, int status);
}
