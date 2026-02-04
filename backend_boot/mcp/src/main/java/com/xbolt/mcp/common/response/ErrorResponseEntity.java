package com.xbolt.mcp.common.response;

import com.xbolt.mcp.common.enumType.ErrorCode;
import lombok.Builder;
import lombok.Data;
import org.springframework.http.ResponseEntity;

@Data
@Builder
public class ErrorResponseEntity {

    private int status;
    private String code;
    private String message;
    public static ResponseEntity<ErrorResponseEntity> toResponseEntity(ErrorCode e){
        return ResponseEntity
                .status(e.getHttpStatus())
                .body(ErrorResponseEntity.builder()
                        .status(e.getHttpStatus().value())
                        .message(e.getMessage())
                        .build()
                ) ;
    }

    public static ErrorResponseEntity of(ErrorCode e){
        return ErrorResponseEntity.builder()
                .status(e.getHttpStatus().value())
                .message(e.getMessage())
                .build();
    }
}