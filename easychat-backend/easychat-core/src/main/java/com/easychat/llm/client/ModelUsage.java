package com.easychat.llm.client;
/** Values are provider-reported, never estimated from text length. */
public record ModelUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens, String finishReason) {}