package com.easychat.common.util;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class SSEUtil {

    public static SseEmitter createEmitter() {
        return new SseEmitter(3600000L);
    }

    public static void sendMessage(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("message").data(message));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendThought(SseEmitter emitter, String jsonData) {
        try {
            emitter.send(SseEmitter.event().name("thought").data(jsonData));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendAction(SseEmitter emitter, String jsonData) {
        try {
            emitter.send(SseEmitter.event().name("action").data(jsonData));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendObservation(SseEmitter emitter, String jsonData) {
        try {
            emitter.send(SseEmitter.event().name("observation").data(jsonData));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendError(SseEmitter emitter, String errorMessage) {
        try {
            emitter.send(SseEmitter.event().name("error").data(errorMessage));
            emitter.completeWithError(new RuntimeException(errorMessage));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendFinish(SseEmitter emitter, String finishReason) {
        try {
            emitter.send(SseEmitter.event().name("finish").data(finishReason));
            emitter.complete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
