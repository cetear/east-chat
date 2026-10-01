package com.easychat.tools.impl;
import com.easychat.tools.Tool;
import org.springframework.stereotype.Component;
import java.util.Map;
@Component
public class HelloTool implements Tool {
    public String name() { return "hello"; }
    public String description() { return "Return hello. No arguments required."; }
    public String execute(Map<String, Object> args) { return "hello"; }
}
