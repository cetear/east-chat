package com.easychat.api.config;
import com.easychat.common.exception.BusinessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.Objects;
@lombok.extern.slf4j.Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> status(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("error",Objects.toString(e.getReason(),"Request failed"))); }
    @ExceptionHandler({IllegalArgumentException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<?> invalid(Exception e) { return ResponseEntity.badRequest().body(Map.of("error",Objects.toString(e.getMessage(),"Invalid request"))); }
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> business(BusinessException e) {
        int code = "409".equals(e.getCode()) ? 409 : "404".equals(e.getCode()) ? 404 : 400;
        return ResponseEntity.status(code).body(Map.of("error",e.getMessage()));
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<?> tooLarge(Exception e) { return ResponseEntity.status(413).body(Map.of("error","File too large (max 20MB)")); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> failure(Exception e) {
        String errorId = java.util.UUID.randomUUID().toString();
        log.error("Unhandled API failure errorId={}", errorId, e);
        return ResponseEntity.internalServerError().header("X-Error-Id", errorId)
                .body(Map.of("error", "Request failed"));
    }
}
