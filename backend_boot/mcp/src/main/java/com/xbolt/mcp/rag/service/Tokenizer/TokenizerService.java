package com.xbolt.mcp.rag.service.Tokenizer;

import ch.qos.logback.core.subst.Tokenizer;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TokenizerService {

    private final JTokkitTokenCountEstimator tokenCountEstimator;

    /** 문단 단위 분리 */
    public List<String> splitToParagraphs(String content) {
        return Arrays.stream(content.split("\n\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /** 문장 단위 분리 (간단 구현) */
    public List<String> splitToSentences(String paragraph) {
        return Arrays.asList(paragraph.split("(?<=[.!?])\\s+"));
    }

    /** 토큰 수 계산 */
    public int countTokens(String text) {
        return tokenCountEstimator.estimate(text);
    }



}
