package com.tianji.learning.utils;

/**
 * ThreadLocal封装类
 */
public class TableInfoContext {
    private static final ThreadLocal<String> TL = new ThreadLocal<>();

    public static void setInfo(String info) {
        TL.set(info);
    }

    public static String getInfo() {
        return TL.get();
    }

    public static void remove() {
        TL.remove();
    }
}