package com.github.jarvis.catalina;

/**
 * @author fangtao  2021/12/26 4:27 下午
 * @since 1.0
 */
public interface TomcatRequestTransform {

    /**
     * Tomcat Request完整类路径
     */
    String TOMCAT_REQUEST_CLASS_REFERENCE = "org.apache.catalina.connector.Request";

    /**
     * 改变字节码
     *
     * @param loader 类加载器
     * @return 修改后的字节码
     */
    byte[] transform(ClassLoader loader);
}
