package com.easychat.core.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class EmbeddingIndexRequestedEvent extends ChatEvent {
}
