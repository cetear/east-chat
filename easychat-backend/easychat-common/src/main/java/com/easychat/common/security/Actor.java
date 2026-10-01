package com.easychat.common.security;

import java.util.Set;

public record Actor(String userId, Set<String> roles) {
    public Actor {
        if (userId == null || userId.isBlank() || userId.length() > 128)
            throw new IllegalArgumentException("Invalid identity");
        roles = Set.copyOf(roles);
    }

    public boolean admin() {
        return roles.contains("admin");
    }

    public static Actor demo() {
        return new Actor("demo", Set.of("admin", "user"));
    }
}