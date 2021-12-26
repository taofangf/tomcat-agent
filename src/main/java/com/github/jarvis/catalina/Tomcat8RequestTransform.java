package com.github.jarvis.catalina;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;

/**
 * Tomcat 版本号 >= 8.5可用
 * {@link com.github.jarvis.servlet.BufferedServletRequestWrapper}
 *
 * @author fangtao  2021/12/26 4:35 下午
 * @see <href="https://github.com/apache/tomcat/blob/8.5.x/java/org/apache/catalina/connector/Request.java"></href>
 * @see <href="https://github.com/apache/tomcat/blob/9.0.x/java/org/apache/catalina/connector/Request.java"></href>
 * @since 1.0
 */
public class Tomcat8RequestTransform implements TomcatRequestTransform {

    /**
     * 重写后的Request#getRequest()方法
     */
    public static final String BODY = "{if (facade == null) {\n" +
            "            facade = new org.apache.catalina.connector.RequestFacade($0);\n" +
            "        }\n" +
            "        if (applicationRequest == null) {\n" +
            "            applicationRequest = facade;\n" +
            "        }\n" +
            "        try {\n" +
            "            if (applicationRequest == null) {\n" +
            "                applicationRequest = new com.github.jarvis.servlet.BufferedServletRequestWrapper(facade);\n" +
            "            }\n" +
            "        } catch (java.lang.Exception e) {\n" +
            "            e.printStackTrace();\n" +
            "        }\n" +
            "        return applicationRequest;}";

    @Override
    public byte[] transform(ClassLoader loader) {
        try {
            ClassPool pool = ClassPool.getDefault();

            pool.appendClassPath(new LoaderClassPath(loader));

            CtClass ctClass = pool.get(TOMCAT_REQUEST_CLASS_REFERENCE);

            CtMethod ctMethod = ctClass.getDeclaredMethod("getRequest");

            ctMethod.setBody(BODY);

            return ctClass.toBytecode();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
