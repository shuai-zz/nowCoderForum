package com.example.shared.utils;

import java.util.UUID;

/**
 * @author zhaoshuai
 */
public class ForumUtil {

    public static String generateUuid() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

}