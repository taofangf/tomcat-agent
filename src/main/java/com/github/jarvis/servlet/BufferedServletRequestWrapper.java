package com.github.jarvis.servlet;


import com.github.jarvis.util.IOUtils;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * 请求的inputStream保存一份，使得可以重复读
 *
 * @author fangtao  2021/12/26 4:50 下午
 * @since 1.0
 */
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
        IOUtils.copy(super.getInputStream(), cacheBytes);

    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }
}
