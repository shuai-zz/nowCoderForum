package com.example.user.domain.entity;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class UserStatisticsTest {

    @Test
    void builderPopulatesAllFields() {
        Date now = new Date();
        UserStatistics stats = UserStatistics.builder()
                .userId(42)
                .receivedLikeCount(10)
                .followerCount(3)
                .followeeCount(7)
                .updatedTime(now)
                .build();

        assertThat(stats.getUserId()).isEqualTo(42);
        assertThat(stats.getReceivedLikeCount()).isEqualTo(10);
        assertThat(stats.getFollowerCount()).isEqualTo(3);
        assertThat(stats.getFolloweeCount()).isEqualTo(7);
        assertThat(stats.getUpdatedTime()).isSameAs(now);
    }

    @Test
    void defaultBuilderHasZeroCounts() {
        UserStatistics stats = UserStatistics.builder().userId(1).build();

        assertThat(stats.getReceivedLikeCount()).isZero();
        assertThat(stats.getFollowerCount()).isZero();
        assertThat(stats.getFolloweeCount()).isZero();
    }

    @Test
    void noArgsConstructorYieldsZeroDefaults() {
        UserStatistics stats = new UserStatistics();
        assertThat(stats.getUserId()).isZero();
        assertThat(stats.getReceivedLikeCount()).isZero();
        assertThat(stats.getFollowerCount()).isZero();
        assertThat(stats.getFolloweeCount()).isZero();
        assertThat(stats.getUpdatedTime()).isNull();
    }
}
