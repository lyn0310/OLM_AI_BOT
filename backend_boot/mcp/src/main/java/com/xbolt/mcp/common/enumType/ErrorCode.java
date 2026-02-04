package com.xbolt.mcp.common.enumType;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum ErrorCode {

    DOCUMENT_LOAD_ERROR(HttpStatus.BAD_REQUEST,  "Document Loading중 오류 발생"),
    INVALID_JSON(HttpStatus.BAD_REQUEST,  "올바르지 않은 데이터 형식"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류 발생"),
    VALID_ERROR(HttpStatus.BAD_REQUEST,  "유효하지 않은 값입니다");

    private final HttpStatus httpStatus;
    private final String message;

}
