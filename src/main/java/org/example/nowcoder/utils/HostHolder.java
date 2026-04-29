package org.example.nowcoder.utils;

import org.example.nowcoder.entity.User;
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
