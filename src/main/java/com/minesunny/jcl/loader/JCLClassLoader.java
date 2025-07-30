package com.minesunny.jcl.loader;

import com.minesunny.jcl.config.JCLProperties;
import com.minesunny.jcl.resource.ResourceEntry;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class JCLClassLoader extends URLClassLoader {

    private static final String CLASS_FILE_SUFFIX = ".class";
    public static Map<String, JCLClassLoader> jclClassLoaders = new ConcurrentHashMap<>();
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
        jclClassLoaders.put(properties.getJarDir(), this);

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
}
