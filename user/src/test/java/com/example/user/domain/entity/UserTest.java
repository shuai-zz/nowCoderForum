package com.example.user.domain.entity;

import com.example.shared.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static User inactive(String code) {
        return User.builder()
                .id(1)
                .username("alice")
                .status(User.STATUS_INACTIVE)
                .activationCode(code)
                .type(User.TYPE_USER)
                .build();
    }

    private static User activated() {
        return User.builder()
                .id(2)
                .username("bob")
                .status(User.STATUS_ACTIVATED)
                .activationCode("CODE")
                .type(User.TYPE_USER)
                .build();
    }

    @Nested
    @DisplayName("isActivated()")
    class IsActivated {
        @Test
        void inactiveUserIsNotActivated() {
            assertThat(inactive("CODE").isActivated()).isFalse();
        }

        @Test
        void activatedUserIsActivated() {
            assertThat(activated().isActivated()).isTrue();
        }
    }

    @Nested
    @DisplayName("canActivateWith(code)")
    class CanActivateWith {
        @Test
        void inactiveUserWithMatchingCodeReturnsTrue() {
            assertThat(inactive("CODE").canActivateWith("CODE")).isTrue();
        }

        @Test
        void inactiveUserWithMismatchedCodeReturnsFalse() {
            assertThat(inactive("CODE").canActivateWith("WRONG")).isFalse();
        }

        @Test
        void alreadyActivatedReturnsFalseEvenWithMatchingCode() {
            assertThat(activated().canActivateWith("CODE")).isFalse();
        }

        @Test
        void nullInputCodeDoesNotMatchNonNullStored() {
            assertThat(inactive("CODE").canActivateWith(null)).isFalse();
        }

        @Test
        void nullStoredCodeAndNullInputDoesNotNpeAndReturnsTrue() {
            // 防 NPE 用了 Objects.equals — null/null 视为相等
            User u = inactive(null);
            assertThat(u.canActivateWith(null)).isTrue();
        }
    }

    @Nested
    @DisplayName("activate()")
    class Activate {
        @Test
        void inactiveUserBecomesActivated() {
            User u = inactive("CODE");
            u.activate();
            assertThat(u.isActivated()).isTrue();
            assertThat(u.getStatus()).isEqualTo(User.STATUS_ACTIVATED);
        }

        @Test
        void activatingAnAlreadyActivatedUserThrows() {
            User u = activated();
            assertThatThrownBy(u::activate)
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("already activated");
        }
    }

    @Nested
    @DisplayName("role checks")
    class RoleChecks {
        @Test
        void plainUserIsNotAdminOrModerator() {
            User u = User.builder().type(User.TYPE_USER).build();
            assertThat(u.isAdmin()).isFalse();
            assertThat(u.isModerator()).isFalse();
            assertThat(u.canTopOrFeature()).isFalse();
        }

        @Test
        void adminIsAdminAndCanTopOrFeature() {
            User u = User.builder().type(User.TYPE_ADMIN).build();
            assertThat(u.isAdmin()).isTrue();
            assertThat(u.isModerator()).isFalse();
            assertThat(u.canTopOrFeature()).isTrue();
        }

        @Test
        void moderatorIsModeratorAndCanTopOrFeature() {
            User u = User.builder().type(User.TYPE_MODERATOR).build();
            assertThat(u.isAdmin()).isFalse();
            assertThat(u.isModerator()).isTrue();
            assertThat(u.canTopOrFeature()).isTrue();
        }
    }
}
