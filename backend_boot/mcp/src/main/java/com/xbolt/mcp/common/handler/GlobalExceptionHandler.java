package com.xbolt.mcp.common.handler;

import com.xbolt.mcp.common.enumType.ErrorCode;
import com.xbolt.mcp.common.exception.CustomException;
import com.xbolt.mcp.common.response.ErrorResponseEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<ErrorResponseEntity> handleCustomException(CustomException e) {
        return ErrorResponseEntity.toResponseEntity(e.getErrorCode());
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponseEntity> handleGenericException(Exception e) {
        return ErrorResponseEntity.toResponseEntity(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
