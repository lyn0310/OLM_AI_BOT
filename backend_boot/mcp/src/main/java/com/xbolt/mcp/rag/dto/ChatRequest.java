package com.xbolt.mcp.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ChatRequest {

    @JsonProperty("message")
    private String message;

    @JsonProperty("session_id")
    private String sessionId;
}
