package com.xbolt.mcp.rag.service.Loader;

import com.xbolt.mcp.rag.record.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.xbolt.mcp.common.util.CustomUtils.isEmpty;
import static com.xbolt.mcp.common.util.ExceptionUtils.wrap;

@Service
public class FileLoaderService {

    private static final Logger log = LoggerFactory.getLogger(FileLoaderService.class);

    @Value("${rag.markdown.path}")
    private String markdownPath;

    /**
     * 모든 Markdown 파일 -> Document 리스트로 변환
     * 1. Resource[] -> List<String>
     * 2. SHA-256 해시 생성
     * 3. Metadata 구성
     * 4. Document 객체 생성
     */
    public List<Document> loadDocuments() throws Exception{

        if(isEmpty(markdownPath)) throw new IllegalStateException("MARKDOWN_PATH environment variable not set");
        Resource[] resources = loadAllMarkdowns();

        return Arrays.stream(resources)
                .map(wrap(this::convertResourcesToDocument))
                .collect(Collectors.toList());
    }


    /**
     * classpath의  *.md 파일 찾기
     * *.md -> Resource[]
     */
    private Resource[] loadAllMarkdowns() throws Exception{

        PathMatchingResourcePatternResolver pathMatchingResourcePatternResolver = new PathMatchingResourcePatternResolver();
        //return pathMatchingResourcePatternResolver.getResources(markdownPath + "/*.md");
        return pathMatchingResourcePatternResolver.getResources(markdownPath + "/**/*.md");
    }


    /**
     * Resource -> Document
     */
    private Document convertResourcesToDocument(Resource resource) throws Exception{

        try(BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))
        ){
            String content = bufferedReader.lines().collect(Collectors.joining("\n"));
            String sha256 = sha256Hex(content);

            Map<String, Object> metadata = Map.of(
                    "source", resource.getFilename(),
                    "doc_type", "markdown",
                    "sha256", sha256,
                    "path", resource.getURL().toString()
            );

            return new Document(content, metadata);
        }



    }

    /**
     * data -> 16진수 문자열
     */
    private String sha256Hex(String data) throws Exception{
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash =digest.digest(data.getBytes(StandardCharsets.UTF_8));

        StringBuilder stringBuilder = new StringBuilder();
        for(byte b : hash){
            stringBuilder.append(String.format("%02x", b));
        }
        return stringBuilder.toString();
    }

}
