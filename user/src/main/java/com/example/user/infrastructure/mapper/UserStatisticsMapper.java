package com.example.user.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.user.domain.entity.UserStatistics;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserStatisticsMapper extends BaseMapper<UserStatistics> {

    default int incrementReceivedLikeCount(int userId, int delta) {
        return update(null, Wrappers.<UserStatistics>lambdaUpdate()
                .setSql("received_like_count = received_like_count + {0}", delta)
                .eq(UserStatistics::getUserId, userId));
    }

    default int incrementFollowerCount(int userId, int delta) {
        return update(null, Wrappers.<UserStatistics>lambdaUpdate()
                .setSql("follower_count = follower_count + {0}", delta)
                .eq(UserStatistics::getUserId, userId));
    }

    default int incrementFolloweeCount(int userId, int delta) {
        return update(null, Wrappers.<UserStatistics>lambdaUpdate()
                .setSql("followee_count = followee_count + {0}", delta)
                .eq(UserStatistics::getUserId, userId));
    }
}
