# Tomcat Agent Request增强
> 该诉求的背景源于需要一个公用组件获取所有http请求的内容(甚至在不修改代码的情况下...)。众所周知Http的请求流只能读一次，通常大家为了获取http请求内容会去重写
> `HttpServletRequestWrapper`类中的`getInputStream()`和`getReader()`方法，然后在web应用中的过滤器或者拦截器中将原始的Http
> 请求进行包装即可，这样做针对单个应用没有问题，但是如果想更加通用，那么就得在更源头解决，即Tomcat层面。

## 在不修改代码的情况下实现功能
> 要想无感知，那就得使大招，修改字节码了! 这里我选择的是javassist,当然这里的选择有很多，能实现即可。

于是开始Debug Tomcat源码，找一个合适的地方去把原始的HttpServletRequest进行包装，那么后面想怎么用就怎么用。最开始下手调试的是版本是Tomcat 8.5.x，
```java
package org.apache.catalina.connector;
/**
 * Wrapper object for the Coyote request.
 *
 * @author Remy Maucherat
 * @author Craig R. McClanahan
 */
public class Request implements HttpServletRequest {

    private HttpServletRequest applicationRequest = null;
    
    /**
     * The facade associated with this request.
     */
    protected RequestFacade facade = null;

    // 其他非关键代码省略...

    /**
     * @return the <code>ServletRequest</code> for which this object
     * is the facade.  This method must be implemented by a subclass.
     */
    public HttpServletRequest getRequest() {
        if (facade == null) {
            facade = new RequestFacade(this);
        }
        if (applicationRequest == null) {
            applicationRequest = facade;
        }
        // 这个地方下手好像非常合适!!!
        return applicationRequest;
    }
}

```


地方找到了接下来就重写`HttpServletRequestWrapper`
```java
public class BufferedServletRequestWrapper extends HttpServletRequestWrapper {

    private ByteArrayOutputStream cacheBytes;

    /**
     * Constructs a request object wrapping the given request.
     *
     * @param request
     * @throws IllegalArgumentException if the request is null
     */
    public BufferedServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (cacheBytes == null) {
            cacheInputStream();
        }
        return new BufferedServletInputStream(cacheBytes.toByteArray());
    }

    private void cacheInputStream() throws IOException {
        cacheBytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n = 0;
        while (-1 != (n = super.getInputStream().read(buffer))) {
            cacheBytes.write(buffer, 0, n);
        }
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }
}

public class BufferedServletInputStream extends ServletInputStream {

    private final ByteArrayInputStream inputStream;

    public BufferedServletInputStream(byte[] buffer) {
        this.inputStream = new ByteArrayInputStream(buffer);
    }

    @Override
    public int available() throws IOException {
        return inputStream.available();
    }

    @Override
    public int read() throws IOException {
        return inputStream.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return inputStream.read(b, off, len);
    }


}
```

**这里有几点需要注意**
- 构造方法没有重写,而是在`getInputStream()`中去保存一份
    - 网上大多数的的写法是在构造方法中调用getInputStream保存原始流，但是这会有一个问题，
      即当Content-Type为表单的时候是无法获取到值的
- 最合理的做法还是加上Content-Type的判断,你永远无法知道有人会将表单请求在后台处理的时候调用getInputStream来取值
这是件很可怕的事情,而且你还没有一点办法的...

经过修改Tomcat源码进行调试后发现此方案可行,下一步就要把这个修改源码的操作独立出来。
```java
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
        "                applicationRequest = new com.github.jarvis.servlet.BufferedServletRequestWrapper(facade);\n" +
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

```
核心操作就是上面所说的包装Tomcat内部的HttpServletRequest来满足诉求。

## 部署应用

### 修改启动脚本
增加javaagent参数在应用启动的时候加载字节码修改。
```shell
-javaagent:/path/tomcat-agent.jar
```

## 注意
- 如果报BufferedServletRequestWrapper类不存在的问题则需要将`BufferedServletInputStream`和`BufferedServletRequestWrapper`
  这两个类打成jar包放到tomcat目录下（catalina/lib），让初始化的时候能够加载到即可。


- 由于考虑到适配不同的Tomcat版本,就分别调试了7.x、8.0.x、8.5x、9.x、10.x;发现在8.0之前有点不一样，于是将字节码修改的部分做了根据Tomcat版本来做不同的调整。以下为Tomcat 7.0.109 版本部分代码
  
  ```java
  package org.apache.catalina.connector;
  /**
   * Wrapper object for the Coyote request.
   *
   * @author Remy Maucherat
   * @author Craig R. McClanahan
   */
  public class Request implements HttpServletRequest {
  
      /**
       * The facade associated with this request.
       */
      protected RequestFacade facade = null;
  
      // 部分代码省略...
  
      /**
       * @return the <code>ServletRequest</code> for which this object
       * is the facade.  This method must be implemented by a subclass.
       */
      public HttpServletRequest getRequest() {
          if (facade == null) {
              facade = new RequestFacade(this);
          }
          return facade;
      }
  }
  ```
  
  就是这一部分的差异，那我就直接参考高版本调整了一下。

