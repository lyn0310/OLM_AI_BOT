package com.xbolt.mcp.common.util;

import java.util.function.Function;

public class ExceptionUtils {


    @FunctionalInterface
    public interface CheckedFunction<T, R> {
        R apply(T t) throws Exception;
    }


    public static <T, R> Function<T,R> wrap(CheckedFunction<T,R> function){
        return param -> {
            try{
                return function.apply(param);
            }catch (Exception e ){
                throw new RuntimeException(e);
            }
        };
    }
}
