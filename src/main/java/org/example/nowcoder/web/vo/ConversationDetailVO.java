package org.example.nowcoder.web.vo;

import org.example.nowcoder.web.common.PageResult;

/**
 * 私信会话详情：对方用户 + 本会话的消息分页。
 */
public record ConversationDetailVO(
        UserVO target,
        PageResult<LetterVO> letters
) {}
