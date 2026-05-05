package com.example.message.interfaces.vo;


import com.example.user.interfaces.vo.UserVO;

import java.util.Date;

/**
 * 会话内的单条私信。
 *
 * @param status 0=未读, 1=已读, 2=已删
 */
public record DmVo(
        int id,
        UserVO fromUser,
        String content,
        int status,
        Date createTime
) {
    public static DmVo of(
            int id,
            UserVO fromUser,
            String content,
            int status,
            Date createTime
    ) {
        return new DmVo(
                id,
                fromUser,
                content,
                status,
                createTime
        );
    }
}
