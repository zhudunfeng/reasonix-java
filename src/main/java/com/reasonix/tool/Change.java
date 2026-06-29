package com.reasonix.tool;

import java.util.Map;

/**
 * 变更摘要。
 */
public class Change {

    private final String summary;
    private final Map<String, Object> details;

    public Change(String summary, Map<String, Object> details) {
        this.summary = summary;
        this.details = details;
    }

    public String getSummary() {
        return summary;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
