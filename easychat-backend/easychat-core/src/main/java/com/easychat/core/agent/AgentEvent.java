package com.easychat.core.agent;
import lombok.Data;
import java.util.Map;
@Data
public class AgentEvent {
    public enum Type { THOUGHT, ACTION, OBSERVATION, MESSAGE, ERROR, FINISH, SOURCES, META, WARNING }
    private Type type;
    private String content;
    private Map<String,Object> metadata;
    private String toolName;
    private Map<String, Object> toolInput;
    private AgentEvent(Type type) { this.type = type; }
    private static AgentEvent of(Type type, String content) {
        AgentEvent event = new AgentEvent(type); event.content = content; return event;
    }
    public static AgentEvent thought(String text) { return of(Type.THOUGHT, text); }
    public static AgentEvent message(String text) { return of(Type.MESSAGE, text); }
    public static AgentEvent observation(String text) { return of(Type.OBSERVATION, text); }
    public static AgentEvent error(String text) { return of(Type.ERROR, text); }
    public static AgentEvent finish(String text) { return of(Type.FINISH, text); }
    public static AgentEvent sources(String text) { return of(Type.SOURCES, text); }
    public static AgentEvent meta(Map<String,Object> value) {var event=new AgentEvent(Type.META);event.metadata=value;return event;}
    public static AgentEvent warning(String text) { return of(Type.WARNING, text); }
    public static AgentEvent action(String name, Map<String,Object> args) {
        AgentEvent event = new AgentEvent(Type.ACTION);
        event.toolName = name; event.toolInput = args == null ? Map.of() : args; return event;
    }
}
