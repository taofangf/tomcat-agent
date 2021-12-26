package com.github.jarvis.catalina;

import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.util.Objects;
import java.util.Properties;

/**
 * Tomcat Agent插件
 *
 * @author fangtao  2021/12/26 4:16 下午
 * @since 1.0
 */
public class RequestAgent {

    /**
     * 字节码增强类名称
     */
    public static final String ENHANCE_CLASS_NAME = "org/apache/catalina/connector/Request";


    /**
     * Tomcat内置信息类路径
     */
    public static final String TOMCAT_SERVER_INFO_PATH = "org/apache/catalina/util/ServerInfo.properties";

    /**
     * Tomcat 版本号
     */
    public static final String TOMCAT_SERVER_NUMBER = "server.number";

    public static final String TOMCAT_VERSION_PREFIX_7X = "7.0";
    public static final String TOMCAT_VERSION_PREFIX_8X = "8.0";
    public static final String TOMCAT_VERSION_PREFIX_85X = "8.5";
    public static final String TOMCAT_VERSION_PREFIX_9X = "9.0";
    public static final String TOMCAT_VERSION_PREFIX_10X = "10.0";

    public static void premain(String args, Instrumentation instrumentation) {
        instrumentation.addTransformer(((loader, className, classBeingRedefined, protectionDomain, classfileBuffer) -> {

            if (!ENHANCE_CLASS_NAME.equals(className)) {
                return null;
            }

            Properties properties = new Properties();

            try (InputStream is = loader.getResourceAsStream(TOMCAT_SERVER_INFO_PATH)) {

                properties.load(is);

                String version = properties.getProperty(TOMCAT_SERVER_NUMBER);

                TomcatRequestTransform transform = getTransformByVersion(version);
                if (Objects.nonNull(transform)) {
                    return transform.transform(loader);
                }

            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
            return null;
        }));

    }

    private static TomcatRequestTransform getTransformByVersion(String version) {
        if (version.startsWith(TOMCAT_VERSION_PREFIX_7X) || version.startsWith(TOMCAT_VERSION_PREFIX_8X)) {
            return new Tomcat7RequestTransform();
        } else if (version.startsWith(TOMCAT_VERSION_PREFIX_85X) || version.startsWith(TOMCAT_VERSION_PREFIX_9X)
                || version.startsWith(TOMCAT_VERSION_PREFIX_10X)) {
            return new Tomcat8RequestTransform();
        }
        return null;
    }
}
