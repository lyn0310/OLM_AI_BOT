package com.xbolt.mcp.common.exception;

import com.xbolt.mcp.common.enumType.ErrorCode;
import lombok.Getter;

@Getter
public class CustomException extends RuntimeException{

    ErrorCode errorCode;
    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

}
