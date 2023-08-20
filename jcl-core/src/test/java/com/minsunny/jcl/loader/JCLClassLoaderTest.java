package com.minsunny.jcl.loader;

import com.minsunny.jcl.config.JCLProperties;
import com.mysql.jdbc.Driver;
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
                .replace("jcl-core/target/test-classes/","/jar/mysql8");
        JCLProperties jclProperties = new JCLProperties(jarPath);
        JCLClassLoader jclClassLoader = new JCLClassLoader(jclProperties);
        Class<?> aClass = jclClassLoader.loadClass("com.mysql.jdbc.Driver");
        Assertions.assertEquals(aClass.getClassLoader().getClass(),JCLClassLoader.class);
        Class<?> aClass1 = Class.forName("com.mysql.jdbc.Driver");
        Assertions.assertNotEquals(aClass1.getClassLoader().getClass(),JCLClassLoader.class);

    }

    @Test
    void testLoad2() throws ClassNotFoundException {
        String jarPath = Objects.requireNonNull(JCLClassLoaderTest.class.getClassLoader()
                        .getResource(""))
                .getPath()
                .replace("jcl-core/target/test-classes/","/jar/mysql8/mysql-connector-java-8.0.30.jar");
        JCLProperties jclProperties = new JCLProperties(jarPath);
        JCLClassLoader jclClassLoader = new JCLClassLoader(jclProperties);
        Class<?> aClass = jclClassLoader.loadClass("com.mysql.jdbc.Driver");
        Assertions.assertEquals(aClass.getClassLoader().getClass(),JCLClassLoader.class);
    }

    @Test
    void testLoad3() throws ClassNotFoundException {
        String jarPath = Objects.requireNonNull(JCLClassLoaderTest.class.getClassLoader()
                        .getResource(""))
                .getPath()
                .replace("jcl-core/target/test-classes/","/jar/mysql8/mysql-connector-java-8.0.30.jar");
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
                .replace("jcl-core/target/test-classes/","/jar/dep");
        JCLProperties jclProperties = new JCLProperties(jarPath);
        JCLClassLoader jclClassLoader = new JCLClassLoader(jclProperties);
        Class<?> aClass = jclClassLoader.loadClass("com.minsunny.jcl.dep.DepTest");

        Class<?> aClass1 = jclClassLoader.loadClass("lombok.AccessLevel");
        Assertions.assertEquals(aClass1.getClassLoader(), aClass.getClassLoader());
        Assertions.assertNotEquals(Class.forName("lombok.AccessLevel"),aClass1);
    }
}