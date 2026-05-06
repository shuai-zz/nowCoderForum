package com.example.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("user_statistics")
public class UserStatistics {
    @TableId(type = IdType.INPUT)
    private int userId;
    private int receivedLikeCount;
    private int followerCount;
    private int followeeCount;
    private Date updatedTime;
}
