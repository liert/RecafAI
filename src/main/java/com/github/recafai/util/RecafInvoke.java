package com.github.recafai.util;

import org.slf4j.Logger;
import software.coley.recaf.analytics.logging.Logging;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

public class RecafInvoke {
    private static final Logger logger = Logging.get(RecafInvoke.class);
    private URLClassLoader loader;

    public RecafInvoke() {
        loader = new URLClassLoader(new URL[]{}, ClassLoader.getSystemClassLoader());
    }

    public void addLibrary(String file, Method method) throws NoSuchMethodException, MalformedURLException, InvocationTargetException, IllegalAccessException {
        if (method == null) {
            method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
        }
        method.invoke(loader, new File(file).toURI().toURL());
    }

    public void addLibraries(List<String> files) {
        try {
            Method addURLMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURLMethod.setAccessible(true);
            for (String file : files) {
                addLibrary(file, addURLMethod);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String invoke(String className, String methodName, String string) {
        try {
            logger.info("Decrypting: " + className + "." + methodName + "(" + string + ")");
            Class<?> clazz = loader.loadClass(className.replace('/', '.'));
            for (Method method : clazz.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (method.getName().equals(methodName) &&
                        method.getReturnType().equals(String.class) &&
                        method.getParameterCount() == 1 &&
                        parameters[0].equals(String.class)
                ) {
                    String result = (String) method.invoke(loader, string);
                    return result == null ? "[Recaf] Decrypt Error!" : result;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "[Recaf] Decrypt Error!";
    }
}
