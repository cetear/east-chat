package com.easychat.core.agent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReActOutputParser {

    private static final Pattern THOUGHT_PATTERN = Pattern.compile(
        "Thought:\\s*(.+?)(?=\\n(?:Action:|Final Answer:)|$)", Pattern.DOTALL);
    private static final Pattern ACTION_PATTERN = Pattern.compile(
        "Action:\\s*(.+?)(?=\\n|$)");
    private static final Pattern ACTION_INPUT_PATTERN = Pattern.compile(
        "Action Input:\\s*(.+?)(?=\\n(?:Thought:|Observation:|$))", Pattern.DOTALL);
    private static final Pattern FINAL_ANSWER_PATTERN = Pattern.compile(
        "Final Answer:\\s*(.+)", Pattern.DOTALL);

    public static ReActStep parse(String llmOutput) {
        ReActStep step = new ReActStep();

        if (llmOutput == null || llmOutput.isBlank()) {
            step.setFinalAnswer("");
            return step;
        }

        String trimmed = llmOutput.trim();

        // Extract Thought
        Matcher thoughtMatcher = THOUGHT_PATTERN.matcher(trimmed);
        if (thoughtMatcher.find()) {
            step.setThought(thoughtMatcher.group(1).trim());
        }

        // Extract Final Answer (check first — if present, no action needed)
        Matcher finalMatcher = FINAL_ANSWER_PATTERN.matcher(trimmed);
        if (finalMatcher.find()) {
            step.setFinalAnswer(finalMatcher.group(1).trim());
            return step;
        }

        // Extract Action
        Matcher actionMatcher = ACTION_PATTERN.matcher(trimmed);
        if (actionMatcher.find()) {
            step.setAction(actionMatcher.group(1).trim());
        }

        // Extract Action Input
        Matcher inputMatcher = ACTION_INPUT_PATTERN.matcher(trimmed);
        if (inputMatcher.find()) {
            step.setActionInput(inputMatcher.group(1).trim());
        }

        // If no structured output was parsed, treat entire output as final answer
        if (step.getThought() == null && step.getAction() == null && step.getFinalAnswer() == null) {
            step.setFinalAnswer(trimmed);
        }

        return step;
    }
}
