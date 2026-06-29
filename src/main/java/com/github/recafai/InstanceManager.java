package com.github.recafai;

import software.coley.recaf.services.plugin.CdiClassAllocator;

public class InstanceManager {
    public static CdiClassAllocator cdiClassAllocator;

    public static <T> T getBean(Class<T> cls) {
        try {
            if (cdiClassAllocator == null) {
                throw new IllegalStateException("CdiClassAllocator not initialized");
            }
            return cdiClassAllocator.instance(cls);
        } catch (Exception e) {
            // 记录异常但不抛出，避免插件启动失败
            System.err.println("Failed to get bean for class " + cls.getName() + ": " + e.getMessage());
            return null;
        }
    }
}
