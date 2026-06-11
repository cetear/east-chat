package com.easychat.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
public class SSEUtil {

    public static SseEmitter createEmitter() {
        return new SseEmitter(3600000L);
    }

    public static boolean sendMessage(SseEmitter emitter, String message) {
        return send(emitter, "message", message);
    }

    public static boolean sendThought(SseEmitter emitter, String jsonData) {
        return send(emitter, "thought", jsonData);
    }

    public static boolean sendAction(SseEmitter emitter, String jsonData) {
        return send(emitter, "action", jsonData);
    }

    public static boolean sendObservation(SseEmitter emitter, String jsonData) {
        return send(emitter, "observation", jsonData);
    }

    public static boolean sendError(SseEmitter emitter, String errorMessage) {
        boolean sent = send(emitter, "error", errorMessage != null ? errorMessage : "stream failed");
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("SSE emitter complete after error failed: {}", e.getMessage());
        }
        return sent;
    }

    public static boolean sendFinish(SseEmitter emitter, String finishReason) {
        boolean sent = send(emitter, "finish", finishReason);
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("SSE emitter complete failed: {}", e.getMessage());
        }
        return sent;
    }

    private static boolean send(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data != null ? data : ""));
            return true;
        } catch (Exception e) {
            log.debug("SSE send failed, event={}, reason={}", eventName, e.getMessage());
            return false;
        }
    }
}
