package com.example.message.interfaces.vo;


import com.example.shared.result.PageResult;
import com.example.user.interfaces.vo.UserVO;

/**
 * 私信会话详情：对方用户 + 本会话的消息分页。
 */
public record ConversationDetailVO(
        UserVO target,
        PageResult<DmVo> letters
) {}
