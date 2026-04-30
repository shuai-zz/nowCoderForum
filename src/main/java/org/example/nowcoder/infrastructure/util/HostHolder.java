package org.example.nowcoder.infrastructure.util;

import org.example.nowcoder.domain.entity.User;
import org.springframework.stereotype.Component;

/**
 * @author 23211
 */
@Component
@Deprecated
public class HostHolder {
    private final ThreadLocal<User> users = new ThreadLocal<>();
    public void setUser(User user) {
        users.set(user);
    }

    public User getUser() {
        return users.get();
    }

    public void clear() {
        users.remove();
    }
}
