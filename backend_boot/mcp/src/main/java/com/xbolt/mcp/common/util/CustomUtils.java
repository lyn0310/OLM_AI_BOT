package com.xbolt.mcp.common.util;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public class CustomUtils {

    public static boolean isEmpty(String str) {
        return (str == null) || (str.isBlank());
    }

    public static boolean isEmpty(List<?> list) {
        return (list == null) || (list.isEmpty());
    }

    public static boolean isEmpty(Long val) { return (val == null) || (val == 0L); }

    public static boolean isEmpty(Integer val) { return (val == null); }

    public static boolean isEmpty(Boolean val) { return (val == null); }

    public static boolean isEmpty(MultipartFile file) { return (file == null) || (file.isEmpty());}

    public static boolean isEmpty(float[] val) { return (val == null) || (val.length == 0);}

}
