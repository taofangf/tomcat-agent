package com.github.jarvis.catalina;


import javassist.*;

/**
 * Tomcat 7.x 或者 < Tomcat 8.5时可用
 * {@link com.github.jarvis.servlet.BufferedServletRequestWrapper}
 *
 * @author fangtao  2021/12/26 4:35 下午
 * @see <href="https://github.com/apache/tomcat/blob/7.0.109/java/org/apache/catalina/connector/Request.java"></href>
 * @since 1.0
 */
public class Tomcat7RequestTransform implements TomcatRequestTransform {

    /**
     * 重写后的Request#getRequest()方法
     */
    public static final String BODY = "{if (facade == null) {\n" +
            "            facade = new org.apache.catalina.connector.RequestFacade($0);\n" +
            "        }\n" +
            "        try {\n" +
            "            if (requestWrapper == null) {\n" +
            "                requestWrapper = new com.github.jarvis.servlet.BufferedServletRequestWrapper(facade);\n" +
            "            }\n" +
            "            return requestWrapper;\n" +
            "        } catch (java.lang.Exception e) {\n" +
            "            e.printStackTrace();\n" +
            "        }\n" +
            "        return facade;}";

    @Override
    public byte[] transform(ClassLoader loader) {
        try {
            ClassPool pool = ClassPool.getDefault();

            pool.appendClassPath(new LoaderClassPath(loader));

            CtClass ctClass = pool.get(TOMCAT_REQUEST_CLASS_REFERENCE);

            // 创建局部变量
            ctClass.addField(CtField.make("private javax.servlet.http.HttpServletRequest requestWrapper = null;", ctClass));

            CtMethod ctMethod = ctClass.getDeclaredMethod("getRequest");

            ctMethod.setBody(BODY);


            // 重置变量为空，否则Request对象保持不变造成整体异常
            CtMethod recycleMethod = ctClass.getDeclaredMethod("recycle");

            recycleMethod.insertAfter("$0.requestWrapper = null;");

            return ctClass.toBytecode();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}
