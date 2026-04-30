package org.example.nowcoder.interfaces.vo;

import org.example.nowcoder.interfaces.common.PageResult;

/**
 * 私信会话详情：对方用户 + 本会话的消息分页。
 */
public record ConversationDetailVO(
        UserVO target,
        PageResult<LetterVO> letters
) {}
