package com.minesunny.jcl.loader;

import com.minesunny.jcl.config.JCLProperties;
import com.minesunny.jcl.resource.ResourceEntry;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class JCLClassLoader extends URLClassLoader {

    private static final String CLASS_FILE_SUFFIX = ".class";
    public static Map<String, WeakReference<JCLClassLoader>> jclClassLoaders = new ConcurrentHashMap<>();
    /**
     * The parent class loader.
     */
    protected final ClassLoader parent;
    protected final Map<String, ResourceEntry> resourceEntries =
            new ConcurrentHashMap<>();
    private final ClassLoader javaseClassLoader;
    private JCLProperties properties;

    public JCLClassLoader(JCLProperties properties) {
        this(properties.getUrls(), properties.getParentLoader());
        this.properties = properties;
        jclClassLoaders.put(properties.getJarDir(), new WeakReference<>(this));

    }

    protected JCLClassLoader(URL[] urls, ClassLoader parent) {

        super(urls, parent);
        ClassLoader p = getParent();
        if (p == null) {
            p = getSystemClassLoader();
        }
        this.parent = p;

        ClassLoader j = String.class.getClassLoader();
        if (j == null) {
            j = getSystemClassLoader();
            while (j.getParent() != null) {
                j = j.getParent();
            }
        }
        this.javaseClassLoader = j;

    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return loadClass(name, false);
    }

    protected Class<?> findLocalLoadedClass(String name) {
        String path = binaryNameToPath(name);
        ResourceEntry entry = resourceEntries.get(path);
        if (entry != null) {
            return entry.loadedClass;
        }
        return null;
    }

    protected Class<?> findClassInternal(String name) {

        if (name == null) {
            return null;
        }
        String path = binaryNameToPath(name);
        ResourceEntry entry = resourceEntries.get(path);
        if (entry == null) {
            entry = new ResourceEntry();
            entry.lastModified = System.currentTimeMillis();
            // Add the entry in the local resource repository
            synchronized (resourceEntries) {
                // Ensures that all the threads which may be in a race to load
                // a particular class all end up with the same ResourceEntry
                // instance
                ResourceEntry entry2 = resourceEntries.get(path);
                if (entry2 == null) {
                    resourceEntries.put(path, entry);
                } else {
                    entry = entry2;
                }
            }
        }

        Class<?> clazz = entry.loadedClass;
        if (clazz != null) {
            return clazz;
        }
        synchronized (getClassLoadingLock(name)) {
            clazz = entry.loadedClass;
            if (clazz != null) {
                return clazz;
            }
            try {

                clazz = super.findClass(name);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            entry.loadedClass = clazz;
        }
        return clazz;
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            if (log.isDebugEnabled()) {
                log.debug("loadClass(" + name + ", " + resolve + ")");
            }
            Class<?> clazz;

            // 检查 本地class cache
            clazz = findLocalLoadedClass(name);
            if (clazz != null) {
                if (log.isDebugEnabled()) {
                    log.debug("本地类加载器的类缓存存在类：{}", clazz.getName());
                }
                if (resolve) {
                    resolveClass(clazz);
                }
                return clazz;
            }

            // 检查父加载器的class cache
            clazz = findLoadedClass(name);
            if (clazz != null) {
                if (log.isDebugEnabled()) {
                    log.debug("父类加载器类缓存存在类：{}", clazz.getName());
                }
                if (resolve) {
                    resolveClass(clazz);
                }
                return clazz;
            }

            String resourceName = binaryNameToPath(name);
            // 看这个类是纳管
            if (findResource(resourceName) != null) {
                if (log.isDebugEnabled()) {
                    log.debug("  Searching local repositories");
                }
                try {
                    clazz = findClass(name);
                    if (clazz != null) {
                        if (log.isDebugEnabled()) {
                            log.debug("  Loading class from local repository");
                        }
                        if (resolve) {
                            resolveClass(clazz);
                        }
                        return clazz;
                    }
                } catch (ClassNotFoundException e) {
                    // Ignore
                }
            }


            ClassLoader javaSeLoader = getJavaseClassLoader();
            boolean tryLoadingFromJavaSeLoader;
            try {
                URL url = javaSeLoader.getResource(resourceName);
                tryLoadingFromJavaSeLoader = (url != null);
            } catch (Throwable t) {
                tryLoadingFromJavaSeLoader = true;
            }
            if (tryLoadingFromJavaSeLoader) {
                try {
                    clazz = javaSeLoader.loadClass(name);
                    if (clazz != null) {
                        if (resolve) {
                            resolveClass(clazz);
                        }
                        return clazz;
                    }
                } catch (ClassNotFoundException ignored) {
                }
            }
            boolean delegateLoad = parent != null;

            // (1) Delegate to our parent if requested
            if (delegateLoad) {
                if (log.isDebugEnabled()) {
                    log.debug("  Delegating to parent classloader1 " + parent);
                }
                try {
                    clazz = Class.forName(name, false, parent);
                    if (log.isDebugEnabled()) {
                        log.debug("  Loading class from parent");
                    }
                    if (resolve) {
                        resolveClass(clazz);
                    }
                    return clazz;
                } catch (ClassNotFoundException e) {
                    // Ignore
                }
            }
        }
        throw new ClassNotFoundException(name);
    }

    /**
     * @param name the name of the class
     * @return the resulting class
     * @throws ClassNotFoundException if the class could not be found,
     *                                or if the loader is closed.
     * @throws NullPointerException   if {@code name} is {@code null}.
     */
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (log.isDebugEnabled()) {
            log.debug("    findClass(" + name + ")");
        }

        Class<?> clazz;
        try {
            if (log.isTraceEnabled()) {
                log.trace("      findClassInternal(" + name + ")");
            }
            try {
                clazz = findClassInternal(name);

            } catch (RuntimeException e) {
                if (log.isTraceEnabled()) {
                    log.trace("      -->RuntimeException Rethrown", e);
                }
                throw e;
            }
            if (clazz == null) {
                if (log.isDebugEnabled()) {
                    log.debug("    --> Returning ClassNotFoundException");
                }
                throw new ClassNotFoundException(name);
            }
        } catch (ClassNotFoundException e) {
            if (log.isTraceEnabled()) {
                log.trace("    --> Passing on ClassNotFoundException");
            }
            throw e;
        }
        // Return the class we have located
        if (log.isTraceEnabled()) {
            log.debug("      Returning class " + clazz);
        }
        return clazz;
    }

    private String binaryNameToPath(String binaryName) {
        // 1 for leading '/', 6 for ".class"
        return binaryName.replace('.', '/') +
                CLASS_FILE_SUFFIX;
    }

    protected ClassLoader getJavaseClassLoader() {
        return javaseClassLoader;
    }

    @Override
    public void close() throws IOException {
        super.close();
    }

    /**
     * 释放当前类加载器：
     * 1. 注销 JDBC DriverManager 中由当前 ClassLoader 注册的驱动（防止 JVM 级别的强引用泄露）
     * 2. 关闭 MySQL 清理线程（如果存在）
     * 3. 从静态注册表中注销 WeakReference
     * 4. 清理本地缓存资源/Class 引用
     * 5. 关闭 URLClassLoader 释放已打开的 jar 文件句柄
     *
     * @throws IOException 如果关闭底层资源失败
     */
    public void release() throws IOException {
        deregisterJdbcDrivers();
        shutdownAbandonedConnectionCleanupThread();
        try {
            java.util.ResourceBundle.clearCache(this);
        } catch (Throwable ignored) {
        }
        try {
            java.beans.Introspector.flushCaches();
        } catch (Throwable ignored) {
        }
        if (this.properties != null && this.properties.getJarDir() != null) {
            jclClassLoaders.remove(this.properties.getJarDir());
        }
        this.resourceEntries.clear();
        this.close();
    }

    /**
     * 注销由当前 ClassLoader 加载并注册到 DriverManager 中的所有 JDBC Driver。
     * 由于 DriverManager.getDrivers() 和 deregisterDriver() 具有 CallerSensitive 校验，
     * 必须由当前 ClassLoader 内部加载的类来调用，才能感知并注销自身注册的 Driver。
     */
    private void deregisterJdbcDrivers() {
        try {
            // 通过当前 ClassLoader 动态加载/定义清洁工类，使得调用 DriverManager 的 caller 是当前加载器
            String cleanerClassName = JdbcDriverCleaner.class.getName();
            String path = cleanerClassName.replace('.', '/') + ".class";
            try (java.io.InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
                if (in != null) {
                    byte[] bytes = in.readAllBytes();
                    Class<?> cleanerClass = defineClass(cleanerClassName, bytes, 0, bytes.length);
                    Runnable cleaner = (Runnable) cleanerClass.getDeclaredConstructor().newInstance();
                    cleaner.run();
                }
            }
        } catch (Throwable t) {
            log.warn("注销 JDBC 驱动失败: {}", t.getMessage());
        }
    }

    /**
     * 关闭 MySQL Connector 派生的后台清理线程 AbandonedConnectionCleanupThread（如果已启动）
     */
    private void shutdownAbandonedConnectionCleanupThread() {
        try {
            Class<?> threadClass = null;
            try {
                threadClass = loadClass("com.mysql.cj.jdbc.AbandonedConnectionCleanupThread");
            } catch (Throwable ignored) {
            }
            if (threadClass != null) {
                java.lang.reflect.Method uncheckedShutdown = threadClass.getMethod("uncheckedShutdown");
                uncheckedShutdown.invoke(null);

                // 等待 ExecutorService 线程池中的 Worker 线程完全退出并释放 ClassLoader
                java.lang.reflect.Field executorField = threadClass.getDeclaredField("cleanupThreadExecutorService");
                executorField.setAccessible(true);
                java.util.concurrent.ExecutorService executor = (java.util.concurrent.ExecutorService) executorField.get(null);
                if (executor != null) {
                    executor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS);
                    executorField.set(null, null);
                }

                // 清理 threadRef 及其 ContextClassLoader
                try {
                    java.lang.reflect.Field threadRefField = threadClass.getDeclaredField("threadRef");
                    threadRefField.setAccessible(true);
                    Thread t = (Thread) threadRefField.get(null);
                    if (t != null) {
                        t.setContextClassLoader(null);
                        threadRefField.set(null, null);
                    }
                } catch (Throwable ignored) {
                }

                // 清理 PhantomReferences 集合
                try {
                    java.lang.reflect.Field refsField = threadClass.getDeclaredField("connectionFinalizerPhantomRefs");
                    refsField.setAccessible(true);
                    java.util.Set<?> refs = (java.util.Set<?>) refsField.get(null);
                    if (refs != null) {
                        refs.clear();
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 根据 jarDir 从全局注册表中释放对应的类加载器
     *
     * @param jarDir 资源目录
     * @throws IOException 如果关闭底层资源失败
     */
    public static void release(String jarDir) throws IOException {
        WeakReference<JCLClassLoader> ref = jclClassLoaders.remove(jarDir);
        if (ref != null) {
            JCLClassLoader loader = ref.get();
            if (loader != null) {
                loader.release();
            }
        }
    }
}
