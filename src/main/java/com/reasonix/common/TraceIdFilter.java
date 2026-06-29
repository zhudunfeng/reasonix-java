package com.reasonix.common;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TraceIdFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        if (request instanceof HttpServletRequest httpRequest) {
            String traceId = httpRequest.getHeader("X-Trace-Id");
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString();            }
            try {
                chain.doFilter(new TraceIdRequestWrapper(httpRequest, traceId), response);            } catch (Exception e) {
                throw new RuntimeException(e);            }        } else {
            try {
                chain.doFilter(request, response);            } catch (Exception e) {
                throw new RuntimeException(e);            }        }    }
}