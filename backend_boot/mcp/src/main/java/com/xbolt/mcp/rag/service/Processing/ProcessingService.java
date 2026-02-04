package com.xbolt.mcp.rag.service.Processing;

import com.xbolt.mcp.rag.record.Document;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static com.xbolt.mcp.common.util.ExceptionUtils.wrap;


@Service
public class ProcessingService {

    public List<Document> processingDocuments(List<Document> documents) {

        return documents.stream()
                .map(wrap(this::preprocessDocument))
                .collect(Collectors.toList());
    }
    public Document preprocessDocument(Document document) {

        String pageContent = convertBrToNewline(document.pageContent());

        //pageContent = deleteHtml(pageContent);
        pageContent = lineSpaceNormalization(pageContent);
        pageContent = pIIMasking(pageContent);
        return new Document(pageContent, document.metadata());
    }

    private String convertBrToNewline(String content) {

        return content.replaceAll("(?i)<br\\s*/?>", "\n");
    }
    private String deleteHtml(String pageContent) {;
        return Jsoup.parse(pageContent).text();
    }
    private String lineSpaceNormalization(String pageContent) {
        return pageContent
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String pIIMasking(String pageContent) {
        return pageContent.replaceAll("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", "[REDACTED_EMAIL]")
                .replaceAll("\\b\\d{3}-\\d{3,4}-\\d{4}\\b", "[REDACTED_PHONE]");
    }




}
