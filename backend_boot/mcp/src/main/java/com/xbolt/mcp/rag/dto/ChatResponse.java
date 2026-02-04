package com.xbolt.mcp.rag.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatResponse {

    private String answer;
    private Object graph_data;
    private Object sources;

    public ChatResponse(String answer) {
        this.answer = answer;
    }
}
