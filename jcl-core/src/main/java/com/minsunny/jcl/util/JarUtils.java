package com.minsunny.jcl.util;


import java.io.IOException;
import java.net.URL;
import java.util.jar.JarFile;

public class JarUtils {
    public static URL readJarAsUrl(String jarPath) {
        try (JarFile jarFile = new JarFile(jarPath)) {
            return new URL("file", null, -1, jarFile.getName());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
