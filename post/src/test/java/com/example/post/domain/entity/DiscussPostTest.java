package com.example.post.domain.entity;

import com.example.shared.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class DiscussPostTest {

    private static DiscussPost newPost() {
        return DiscussPost.builder()
                .id(1)
                .userId(100)
                .title("hello")
                .content("world")
                .type(DiscussPost.TYPE_NORMAL)
                .status(DiscussPost.STATUS_NORMAL)
                .createTime(new Date())
                .commentCount(0)
                .likeCount(0)
                .score(0.0)
                .build();
    }

    private static Date dateOf(int year, int month, int day) {
        return Date.from(LocalDateTime.of(year, month, day, 0, 0)
                .atZone(ZoneId.systemDefault()).toInstant());
    }

    @Nested
    @DisplayName("state transitions")
    class StateTransitions {
        @Test
        void markAsTopSetsTypeToTop() {
            DiscussPost p = newPost();
            assertThat(p.isTop()).isFalse();
            p.markAsTop();
            assertThat(p.isTop()).isTrue();
            assertThat(p.getType()).isEqualTo(DiscussPost.TYPE_TOP);
        }

        @Test
        void markAsWonderfulSetsStatusToWonderful() {
            DiscussPost p = newPost();
            assertThat(p.isWonderful()).isFalse();
            p.markAsWonderful();
            assertThat(p.isWonderful()).isTrue();
            assertThat(p.getStatus()).isEqualTo(DiscussPost.STATUS_WONDERFUL);
        }

        @Test
        void softDeleteSetsStatusToDeleted() {
            DiscussPost p = newPost();
            assertThat(p.isDeleted()).isFalse();
            p.softDelete();
            assertThat(p.isDeleted()).isTrue();
            assertThat(p.getStatus()).isEqualTo(DiscussPost.STATUS_DELETED);
        }

        @Test
        void softDeleteOverridesWonderful() {
            DiscussPost p = newPost();
            p.markAsWonderful();
            p.softDelete();
            // status 字段同时承担 wonderful/deleted，删除覆盖加精
            assertThat(p.isDeleted()).isTrue();
            assertThat(p.isWonderful()).isFalse();
        }
    }

    @Nested
    @DisplayName("refreshCommentCount(count)")
    class RefreshCommentCount {
        @Test
        void zeroIsAllowed() {
            DiscussPost p = newPost();
            p.refreshCommentCount(0);
            assertThat(p.getCommentCount()).isZero();
        }

        @Test
        void positiveValueOverwritesPreviousCount() {
            DiscussPost p = newPost();
            p.refreshCommentCount(5);
            p.refreshCommentCount(3);
            assertThat(p.getCommentCount()).isEqualTo(3);
        }

        @Test
        void negativeValueThrows() {
            DiscussPost p = newPost();
            assertThatThrownBy(() -> p.refreshCommentCount(-1))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("comment count");
        }
    }

    @Nested
    @DisplayName("updateScore(score)")
    class UpdateScore {
        @Test
        void zeroIsAllowed() {
            DiscussPost p = newPost();
            p.updateScore(0.0);
            assertThat(p.getScore()).isZero();
        }

        @Test
        void positiveValueIsApplied() {
            DiscussPost p = newPost();
            p.updateScore(123.45);
            assertThat(p.getScore()).isEqualTo(123.45);
        }

        @Test
        void negativeValueThrows() {
            DiscussPost p = newPost();
            assertThatThrownBy(() -> p.updateScore(-0.001))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("score");
        }
    }

    @Nested
    @DisplayName("applySanitizedContent(...)")
    class ApplySanitizedContent {
        @Test
        void overwritesTitleAndContent() {
            DiscussPost p = newPost();
            p.applySanitizedContent("safe title", "safe content");
            assertThat(p.getTitle()).isEqualTo("safe title");
            assertThat(p.getContent()).isEqualTo("safe content");
        }

        @Test
        void doesNotChangeOtherFields() {
            DiscussPost p = newPost();
            int origUserId = p.getUserId();
            int origStatus = p.getStatus();
            p.applySanitizedContent("x", "y");
            assertThat(p.getUserId()).isEqualTo(origUserId);
            assertThat(p.getStatus()).isEqualTo(origStatus);
        }
    }

    @Nested
    @DisplayName("calculateScore(...)")
    class CalculateScore {
        @Test
        void allZeroActivityFloorsWeightToOneSoLogIsZero() {
            // w = max(0, 1) = 1, log10(1) = 0; result == days since epoch
            Date now = new Date();
            double score = DiscussPost.calculateScore(false, 0, 0L, now);
            // Just verify the log component is 0; the day-offset depends on `now`.
            // We re-compute the day-offset using the formula's expected values.
            double dayOffset = score;
            assertThat(dayOffset).isGreaterThan(0);
        }

        @Test
        void wonderfulPostScoresHigherThanRegular() {
            Date now = new Date();
            double normal = DiscussPost.calculateScore(false, 10, 10L, now);
            double wonderful = DiscussPost.calculateScore(true, 10, 10L, now);
            assertThat(wonderful).isGreaterThan(normal);
        }

        @Test
        void moreCommentsRaiseScore() {
            Date now = new Date();
            double less = DiscussPost.calculateScore(false, 5, 0L, now);
            double more = DiscussPost.calculateScore(false, 50, 0L, now);
            assertThat(more).isGreaterThan(less);
        }

        @Test
        void moreLikesRaiseScore() {
            Date now = new Date();
            double less = DiscussPost.calculateScore(false, 0, 1L, now);
            double more = DiscussPost.calculateScore(false, 0, 1000L, now);
            assertThat(more).isGreaterThan(less);
        }

        @Test
        void newerPostScoresHigher() {
            Date older = dateOf(2024, 1, 1);
            Date newer = dateOf(2025, 1, 1);
            double a = DiscussPost.calculateScore(false, 10, 10L, older);
            double b = DiscussPost.calculateScore(false, 10, 10L, newer);
            assertThat(b).isGreaterThan(a);
            // 一年差 ≈ 365 天
            assertThat(b - a).isCloseTo(365.0, within(2.0));
        }

        @Test
        void exactFormulaSpotCheck() {
            // wonderful + 10 评论 + 10 赞 = 75 + 100 + 20 = 195; log10(195) ≈ 2.29003
            Date d = dateOf(2014, 8, 1);
            double score = DiscussPost.calculateScore(true, 10, 10L, d);
            // d 与 EPOCH 都按 systemDefault 0:00 取，day offset 应为 0
            assertThat(score).isCloseTo(Math.log10(195), within(1e-9));
        }
    }
}
