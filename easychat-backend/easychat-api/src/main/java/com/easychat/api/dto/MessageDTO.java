package com.easychat.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class MessageDTO {
    private String role;
    private String content;
    private List<String> images;
}
