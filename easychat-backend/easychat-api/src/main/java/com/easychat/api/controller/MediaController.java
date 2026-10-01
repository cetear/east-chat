package com.easychat.api.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.easychat.media.MediaTranscriber;

@RestController
@RequestMapping("/api/media")
public class MediaController {
    private final MediaTranscriber transcriber;

    public MediaController(MediaTranscriber transcriber) {
        this.transcriber = transcriber;
    }

    @PostMapping("/transcribe")
    public java.util.Map<String, String> transcribe(@RequestParam("file") MultipartFile file) throws java.io.IOException {
        if (file.isEmpty() || file.getSize() > 20 * 1024 * 1024)
            throw new IllegalArgumentException("Media must be 1 byte to 20MB");
        return java.util.Map.of("text", transcriber.transcribe(file.getBytes(), file.getOriginalFilename()));
    }
}