package com.reasonix.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

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