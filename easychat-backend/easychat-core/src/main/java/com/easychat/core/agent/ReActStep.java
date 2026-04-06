package com.easychat.core.agent;

import lombok.Data;

@Data
public class ReActStep {
    private String thought;
    private String action;
    private String actionInput;
    private String finalAnswer;

    public boolean isFinished() {
        return finalAnswer != null && !finalAnswer.isEmpty();
    }

    public boolean hasAction() {
        return action != null && !action.isEmpty();
    }
}
