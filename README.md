# JCL
自定义类加载器，隔离不同版本Jar包，主要解决不同版本的数据库驱动加载问题；

```java

/**
 * 1、 指定jar包路径；可以是一个文件夹也可以是一个jar包; 
 * 比如你想加载jar包A，如果A包没有依赖第三方类，或者第三方类在主程序中可以加载；则jarPath可以直接指定jar包
 * 如果你想加载jar包A，如果A中又第三方类B.class,主程序没有B.class，或者你想单独加载B.class；
 * 则jarPath指定文件夹，文件夹里面放入A的jar包以及类B所在jar包，依次类推；
 */
JCLProperties jclProperties = new JCLProperties(jarPath);

// 2、创建类加载器；所有JCL类加载器都是隔离的；
JCLClassLoader jclClassLoader = new JCLClassLoader(jclProperties);
Class<?> aClass = jclClassLoader.loadClass(className);

```