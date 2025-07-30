package com.minesunny.jcl.loader;

import com.minesunny.jcl.config.JCLProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Objects;

class JCLClassLoaderTest {
    @Test
    void testLoad() throws ClassNotFoundException, SQLException {
        String jarPath = Objects.requireNonNull(JCLClassLoaderTest.class.getClassLoader()
                        .getResource(""))
                .getPath()
                .replace("target/test-classes/", "/jar/mysql8");
        JCLProperties jclProperties = new JCLProperties(jarPath);
        JCLClassLoader jclClassLoader = new JCLClassLoader(jclProperties);
        Class<?> aClass = jclClassLoader.loadClass("com.mysql.jdbc.Driver");
        Assertions.assertEquals(JCLClassLoader.class, aClass.getClassLoader().getClass());
        Class<?> aClass1 = Class.forName("com.mysql.jdbc.Driver");
        Assertions.assertNotEquals(JCLClassLoader.class, aClass1.getClassLoader().getClass());

    }

    @Test
    void testLoad2() throws ClassNotFoundException {
        String jarPath = Objects.requireNonNull(JCLClassLoaderTest.class.getClassLoader()
                        .getResource(""))
                .getPath()
                .replace("target/test-classes/", "/jar/mysql8/mysql-connector-java-8.0.30.jar");
        JCLProperties jclProperties = new JCLProperties(jarPath);
        JCLClassLoader jclClassLoader = new JCLClassLoader(jclProperties);
        Class<?> aClass = jclClassLoader.loadClass("com.mysql.jdbc.Driver");
        Assertions.assertEquals(JCLClassLoader.class, aClass.getClassLoader().getClass());
    }

    @Test
    void testLoad3() throws ClassNotFoundException {
        String jarPath = Objects.requireNonNull(JCLClassLoaderTest.class.getClassLoader()
                        .getResource(""))
                .getPath()
                .replace("target/test-classes/", "/jar/mysql8/mysql-connector-java-8.0.30.jar");
        JCLProperties jclProperties = new JCLProperties(jarPath);
        JCLClassLoader jclClassLoader = new JCLClassLoader(jclProperties);
        Class<?> aClass = jclClassLoader.loadClass("com.mysql.jdbc.Driver");
        Class<?> aClass1 = jclClassLoader.loadClass("java.lang.String");
        Assertions.assertEquals(aClass1, Class.forName("java.lang.String"));

    }

    @Test
    void testLoad4() throws ClassNotFoundException {
        String jarPath = Objects.requireNonNull(JCLClassLoaderTest.class.getClassLoader()
                        .getResource(""))
                .getPath()
                .replace("target/test-classes/", "/jar/dep");
        JCLProperties jclProperties = new JCLProperties(jarPath);
        JCLClassLoader jclClassLoader = new JCLClassLoader(jclProperties);
        Class<?> aClass = jclClassLoader.loadClass("com.minsunny.jcl.dep.DepTest");

        Class<?> aClass1 = jclClassLoader.loadClass("lombok.AccessLevel");
        Assertions.assertEquals(aClass1.getClassLoader(), aClass.getClassLoader());
        Assertions.assertNotEquals(Class.forName("lombok.AccessLevel"), aClass1);
    }
}