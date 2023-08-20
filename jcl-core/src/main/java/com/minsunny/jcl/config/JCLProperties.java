package com.minsunny.jcl.config;

import com.minsunny.jcl.util.JarUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.File;
import java.io.Serializable;
import java.net.URL;
import java.util.Properties;

@Setter
@Getter
@ToString
@EqualsAndHashCode
public class JCLProperties implements Serializable {
    private final String jarDir;
    private URL[] urls;
    private String javaName;
    private String classLoaderName;
    private Properties properties = new Properties();
    private ClassLoader parentLoader = String.class.getClassLoader();

    public JCLProperties(String jarDir) {
        this.jarDir = jarDir;
        File file = new File(jarDir);
        if (file.exists()) {
            if (file.isFile()) {
                this.urls = new URL[1];
                this.urls[0] = JarUtils.readJarAsUrl(file.getAbsolutePath());
            } else {
                File[] files = file.listFiles();
                if (files != null) {
                    this.urls = new URL[files.length];
                    for (int i = 0; i < files.length; i++) {
                        this.urls[i] = JarUtils.readJarAsUrl(files[i].getAbsolutePath());
                    }
                }
            }
        }

    }
}
