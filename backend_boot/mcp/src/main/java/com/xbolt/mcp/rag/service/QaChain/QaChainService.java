package com.xbolt.mcp.rag.service.QaChain;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QaChainService {

    private final ChatModel chatModel;

    public String chat(String question, List<String> contexts) {
        String contextText = String.join("\n\n---\n\n", contexts);

        String systemText = "너는 하림그룹 PI 프로젝트 도우미야. 최대한 간결하게 요약해서 답해.\n제공된 정의서와 그래프 경로를 통합해서 답해.";

        String userText = String.format("""
                [상세 정의서]:
                %s

                질문: %s
                """, contextText, question);

        org.springframework.ai.chat.messages.Message systemMessage = new org.springframework.ai.chat.messages.SystemMessage(
                systemText);
        org.springframework.ai.chat.messages.Message userMessage = new org.springframework.ai.chat.messages.UserMessage(
                userText);

        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

        ChatResponse response = chatModel.call(prompt);
        return response.getResult().getOutput().getText();
    }

}
