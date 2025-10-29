package com.github.recafai;

import software.coley.recaf.services.plugin.CdiClassAllocator;

public class InstanceManager {
    public static CdiClassAllocator cdiClassAllocator;

    public static <T> T getBean(Class<T> cls) {
        try {
            return cdiClassAllocator.instance(cls);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
