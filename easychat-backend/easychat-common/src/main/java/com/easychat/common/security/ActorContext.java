package com.easychat.common.security;

/**
 * HTTP boundary only. Async work receives an explicit Actor/owner id.
 */
public final class ActorContext {
    private static final ThreadLocal<Actor> CURRENT = new ThreadLocal<>();

    public static Actor current() {
        Actor a = CURRENT.get();
        return a == null ? Actor.demo() : a;
    }

    public static void set(Actor a) {
        CURRENT.set(a);
    }

    public static void clear() {
        CURRENT.remove();
    }
}