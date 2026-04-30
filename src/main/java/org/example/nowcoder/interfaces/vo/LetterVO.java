package org.example.nowcoder.interfaces.vo;

import org.example.nowcoder.domain.entity.Message;

import java.util.Date;

/**
 * 会话内的单条私信。
 *
 * @param status 0=未读, 1=已读, 2=已删
 */
public record LetterVO(
        int id,
        UserVO fromUser,
        String content,
        int status,
        Date createTime
) {
    public static LetterVO of(Message m, UserVO from) {
        return new LetterVO(m.getId(), from, m.getContent(), m.getStatus(), m.getCreateTime());
    }
}
