package com.minesunny.jcl.loader;

import com.minesunny.jcl.config.JCLProperties;
import com.sun.management.HotSpotDiagnosticMXBean;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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

    @Test
    void testReleaseAndUnload() throws Exception {
        String jarPath = Objects.requireNonNull(JCLClassLoaderTest.class.getClassLoader()
                        .getResource(""))
                .getPath()
                .replace("target/test-classes/", "/jar/dep");

        // 1. 初始化并加载类，获取 ClassLoader 和 Class 的弱引用
        WeakReference<?>[] refs = createAndLoad(jarPath);
        WeakReference<ClassLoader> loaderRef = (WeakReference<ClassLoader>) refs[0];
        WeakReference<Class<?>> classRef = (WeakReference<Class<?>>) refs[1];

        // 2. 触发垃圾回收并等待释放
        for (int i = 0; i < 10; i++) {
            System.gc();
            if (loaderRef.get() == null && classRef.get() == null) {
                break;
            }
            Thread.sleep(100);
        }

        // 3. 验证 ClassLoader 与 Class 对象均已被 JVM GC 成功卸载与回收
        Assertions.assertNull(loaderRef.get(), "ClassLoader 应在 release 并失去强引用后被 GC 回收");
        Assertions.assertNull(classRef.get(), "Class 对象应在 ClassLoader 回收后被成功卸载与 GC 回收");
    }

    private WeakReference<?>[] createAndLoad(String jarPath) throws Exception {
        JCLProperties jclProperties = new JCLProperties(jarPath);
        JCLClassLoader loader = new JCLClassLoader(jclProperties);

        // 验证注册表中存在弱引用
        Assertions.assertTrue(JCLClassLoader.jclClassLoaders.containsKey(jarPath));
        Assertions.assertNotNull(JCLClassLoader.jclClassLoaders.get(jarPath).get());

        Class<?> loadedClass = loader.loadClass("com.minsunny.jcl.dep.DepTest");
        Assertions.assertNotNull(loadedClass);

        WeakReference<ClassLoader> loaderRef = new WeakReference<>(loader);
        WeakReference<Class<?>> classRef = new WeakReference<>(loadedClass);

        // 调用 release 释放缓存与资源
        loader.release();

        // 验证已从注册表中移除
        Assertions.assertFalse(JCLClassLoader.jclClassLoaders.containsKey(jarPath));

        return new WeakReference<?>[]{loaderRef, classRef};
    }

    @Test
    void testHolderAAndUnload() throws Exception {
        String jarPath = Objects.requireNonNull(JCLClassLoaderTest.class.getClassLoader()
                        .getResource(""))
                .getPath()
                .replace("target/test-classes/", "/jar/dep");

        WeakReference<?>[] refs = createObjectAndUse(jarPath);
        WeakReference<ClassLoader> loaderRef = (WeakReference<ClassLoader>) refs[0];
        WeakReference<Class<?>> classRef = (WeakReference<Class<?>>) refs[1];
        WeakReference<Object> instanceRef = (WeakReference<Object>) refs[2];

        for (int i = 0; i < 20; i++) {
            System.gc();
            if (loaderRef.get() == null && classRef.get() == null && instanceRef.get() == null) {
                break;
            }
            Thread.sleep(100);
        }

        Assertions.assertNull(instanceRef.get(), "对象 A 引用的实例应被 GC 回收");
        Assertions.assertNull(loaderRef.get(), "ClassLoader 应在 release 且 A 释放后被 GC 回收");
        Assertions.assertNull(classRef.get(), "Class 对象应在 ClassLoader 回收后被卸载");
    }

    private WeakReference<?>[] createObjectAndUse(String jarPath) throws Exception {
        JCLProperties jclProperties = new JCLProperties(jarPath);
        JCLClassLoader loader = new JCLClassLoader(jclProperties);

        Class<?> clazz = loader.loadClass("com.minsunny.jcl.dep.DepTest");
        java.lang.reflect.Constructor<?> ctor = clazz.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object obj = ctor.newInstance();

        class HolderA {
            public Object ref;
            public HolderA(Object ref) {
                this.ref = ref;
            }
        }
        HolderA a = new HolderA(obj);
        Assertions.assertNotNull(a.ref);

        WeakReference<ClassLoader> loaderRef = new WeakReference<>(loader);
        WeakReference<Class<?>> classRef = new WeakReference<>(clazz);
        WeakReference<Object> instanceRef = new WeakReference<>(obj);

        // A 释放，obj 释放
        a = null;
        obj = null;
        clazz = null;

        // loader release
        loader.release();

        return new WeakReference<?>[]{loaderRef, classRef, instanceRef};
    }

    @Test
    void testMysqlContainerSqlExecuteAndUnload() throws Exception {
        String jarPath = Objects.requireNonNull(JCLClassLoaderTest.class.getClassLoader()
                        .getResource(""))
                .getPath()
                .replace("target/test-classes/", "/jar/mysql8/mysql-connector-java-8.0.30.jar");

        WeakReference<?>[] refs = executeSqlWithDriver(jarPath);
        WeakReference<ClassLoader> loaderRef = (WeakReference<ClassLoader>) refs[0];
        WeakReference<Class<?>> driverClassRef = (WeakReference<Class<?>>) refs[1];
        WeakReference<Object> driverInstanceRef = (WeakReference<Object>) refs[2];

        // 触发 GC
        for (int i = 0; i < 20; i++) {
            System.gc();
            if (driverInstanceRef.get() == null) {
                break;
            }
            Thread.sleep(100);
        }

        Assertions.assertNull(driverInstanceRef.get(), "Driver 实例应被 GC 回收");
    }

    private WeakReference<?>[] executeSqlWithDriver(String jarPath) throws Exception {
        JCLProperties jclProperties = new JCLProperties(jarPath);
        JCLClassLoader loader = new JCLClassLoader(jclProperties);

        Class<?> driverClass = loader.loadClass("com.mysql.cj.jdbc.Driver");
        Driver driver = (Driver) driverClass.getDeclaredConstructor().newInstance();

        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", "root");
        props.setProperty("password", "root");

        // 连接容器中的 MySQL（禁用 SSL 以免 JSSE 全局 SSLSessionContext 缓存会话对象造成外部强引用）
        try (Connection conn = driver.connect("jdbc:mysql://127.0.0.1:33306/testdb?useSSL=false&allowPublicKeyRetrieval=true&sslMode=DISABLED", props)) {
            Assertions.assertNotNull(conn, "MySQL 连接应创建成功");

            // 1. 测试 UPDATE
            try (Statement stmt = conn.createStatement()) {
                int updatedRows = stmt.executeUpdate("UPDATE user SET name = 'Charlie' WHERE id = 1");
                Assertions.assertEquals(1, updatedRows, "UPDATE 应更新 1 行数据");
            }

            // 2. 测试 QUERY
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM user WHERE id = 1")) {
                Assertions.assertTrue(rs.next(), "QUERY 应查到结果");
                Assertions.assertEquals("Charlie", rs.getString("name"), "查询到的 name 应为更新后的值");
            }
        }

        WeakReference<ClassLoader> loaderRef = new WeakReference<>(loader);
        WeakReference<Class<?>> driverClassRef = new WeakReference<>(driverClass);
        WeakReference<Object> driverInstanceRef = new WeakReference<>(driver);

        // 释放驱动和业务对象
        driver = null;
        driverClass = null;

        // loader release 卸载并释放资源
        loader.release();

        return new WeakReference<?>[]{loaderRef, driverClassRef, driverInstanceRef};
    }

    /**
     * 生成当前 JVM 的堆转储（Heap Dump），仅转储存活对象（live=true）
     */
    public static void dumpHeap(String filePath) throws Exception {
        new File(filePath).delete();
        HotSpotDiagnosticMXBean mxBean = ManagementFactory.newPlatformMXBeanProxy(
                ManagementFactory.getPlatformMBeanServer(),
                "com.sun.management:type=HotSpotDiagnostic",
                HotSpotDiagnosticMXBean.class);
        mxBean.dumpHeap(filePath, true);
    }

    /**
     * 解析 HPROF 堆转储文件，提取当前存活的所有已加载类全限定名集合
     */
    public static Set<String> parseLoadedClassesFromHprof(File hprofFile) throws Exception {
        Set<String> classNames = new HashSet<>();
        Map<Long, String> strings = new HashMap<>();

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(hprofFile)))) {
            // 读取 null 结尾的 HPROF 格式头
            while (in.read() > 0) {}
            int idSize = in.readInt();
            in.readLong(); // 时间戳

            while (true) {
                int tag;
                try {
                    tag = in.readUnsignedByte();
                } catch (EOFException e) {
                    break;
                }
                in.readInt(); // timeDiff
                long length = in.readInt() & 0xFFFFFFFFL;

                if (tag == 0x01) { // STRING 记录
                    long id = (idSize == 4) ? (in.readInt() & 0xFFFFFFFFL) : in.readLong();
                    byte[] bytes = in.readNBytes((int) (length - idSize));
                    strings.put(id, new String(bytes, StandardCharsets.UTF_8));
                } else if (tag == 0x02) { // LOAD CLASS 记录
                    in.readInt(); // serial
                    if (idSize == 4) in.readInt(); else in.readLong(); // classObjId
                    in.readInt(); // stackTrace
                    long classNameId = (idSize == 4) ? (in.readInt() & 0xFFFFFFFFL) : in.readLong();
                    String name = strings.get(classNameId);
                    if (name != null) {
                        classNames.add(name.replace('/', '.'));
                    }
                    long readSoFar = 4 + idSize + 4 + idSize;
                    if (length > readSoFar) {
                        in.skipNBytes(length - readSoFar);
                    }
                } else {
                    in.skipNBytes(length);
                }
            }
        }
        return classNames;
    }

    /**
     * 遍历整个 JAR 包，提取其中定义的所有类（.class 结尾的文件）全限定名列表
     */
    public static List<String> getClassesInJar(File jarFile) throws Exception {
        List<String> list = new ArrayList<>();
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(".class") && !entry.isDirectory()) {
                    String className = name.substring(0, name.length() - 6).replace('/', '.');
                    list.add(className);
                }
            }
        }
        return list;
    }

    /**
     * 测试：使用 JCLClassLoader 加载 jar 包中的所有类，执行 release() 后，
     * 调用 jdump 生成堆转储文件并解析分析，遍历整个 jar 包一一匹配验证所有类均已被 JVM 卸载且在堆中不存在。
     */
    @Test
    void testJarClassesUnloadVerifiedByJdump() throws Exception {
        String jarPath = Objects.requireNonNull(JCLClassLoaderTest.class.getClassLoader()
                        .getResource(""))
                .getPath()
                .replace("target/test-classes/", "/jar/dep/jcl-core-1.0-SNAPSHOT.jar");

        File jarFile = new File(jarPath);
        Assertions.assertTrue(jarFile.exists(), "测试 JAR 文件应存在: " + jarPath);

        // 1. 遍历整个 JAR 包提取所有类名
        List<String> jarClasses = getClassesInJar(jarFile);
        Assertions.assertFalse(jarClasses.isEmpty(), "JAR 中应包含类文件");
        System.out.println("JAR 包中包含类数量: " + jarClasses.size() + ", 列表: " + jarClasses);

        // 2. 加载整个 jar 包中的所有类，并获取弱引用与执行 release()
        Object[] refs = loadAllJarClassesAndRelease(jarPath, jarClasses);
        WeakReference<ClassLoader> loaderRef = (WeakReference<ClassLoader>) refs[0];
        List<WeakReference<Class<?>>> classRefs = (List<WeakReference<Class<?>>>) refs[1];

        // 3. 触发 GC
        for (int i = 0; i < 20; i++) {
            System.gc();
            boolean allNull = (loaderRef.get() == null);
            for (WeakReference<Class<?>> cRef : classRefs) {
                if (cRef.get() != null) {
                    allNull = false;
                    break;
                }
            }
            if (allNull) {
                break;
            }
            Thread.sleep(100);
        }

        // 4. 调用 jdump (Heap Dump) 转储堆内存
        File dumpFile = new File("target/dump_verify.hprof");
        dumpHeap(dumpFile.getAbsolutePath());
        Assertions.assertTrue(dumpFile.exists(), "转储文件应成功生成");

        // 5. 解析 dump 文件，提取存活的所有类
        Set<String> heapClasses = parseLoadedClassesFromHprof(dumpFile);

        // 6. 遍历整个 jar 包，一一匹配验证类是否还存在于堆转储中
        List<String> leakedClasses = new ArrayList<>();
        for (String className : jarClasses) {
            boolean exists = heapClasses.contains(className);
            System.out.println("  匹配验证类 [" + className + "] 是否存在于 dump: " + exists);
            if (exists) {
                leakedClasses.add(className);
            }
        }

        // 清理 dump 文件
        dumpFile.delete();

        // 7. 断言验证：整个 jar 包中的所有类在 release 及 GC 后均不存在于 dump 堆中
        Assertions.assertTrue(leakedClasses.isEmpty(), "以下 jar 中的类未被成功卸载仍存在于 dump 中: " + leakedClasses);
        Assertions.assertNull(loaderRef.get(), "ClassLoader 自身应被成功 GC 回收");
    }

    private Object[] loadAllJarClassesAndRelease(String jarPath, List<String> jarClasses) throws Exception {
        JCLProperties props = new JCLProperties(jarPath);
        JCLClassLoader loader = new JCLClassLoader(props);

        List<WeakReference<Class<?>>> classRefs = new ArrayList<>();
        for (String className : jarClasses) {
            Class<?> clazz = loader.loadClass(className);
            Assertions.assertNotNull(clazz);
            Assertions.assertSame(loader, clazz.getClassLoader(), "类必须由 JCLClassLoader 加载");
            classRefs.add(new WeakReference<>(clazz));
        }

        WeakReference<ClassLoader> loaderRef = new WeakReference<>(loader);

        // 释放加载器与资源
        loader.release();

        return new Object[]{loaderRef, classRefs};
    }
}
