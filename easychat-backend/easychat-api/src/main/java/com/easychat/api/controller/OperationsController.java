package com.easychat.api.controller;

import com.easychat.core.service.OperationsService;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RestController
@RequestMapping("/api/ops")
@RequiredArgsConstructor
public class OperationsController {
    private final OperationsService operations;

    @GetMapping("/status")
    public Map<String, Object> status() {
        return operations.status();
    }
}
