package com.reansonix.common;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class TraceIdRequestWrapper extends HttpServletRequestWrapper {

    private final String traceId;

    public TraceIdRequestWrapper(HttpServletRequest request, String traceId) {
        super(request);
        this.traceId = traceId;    }

    public String getTraceId() {
        return traceId;    }
    @Override
    public String getHeader(String name) {
        if ("X-Trace-Id".equalsIgnoreCase(name)) {
            return traceId;        }
        return super.getHeader(name);    }
}