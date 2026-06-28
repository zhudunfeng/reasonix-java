package com.reansonix.tool;

/**
 * 文件变更预览
 */
public class ChangePreview {
    private final boolean binary;
    private final String diff;

    private ChangePreview(boolean binary, String diff) {
        this.binary = binary;
        this.diff = diff;
    }

    public static ChangePreview of(boolean binary, String diff) {
        return new ChangePreview(binary, diff);
    }

    public boolean binary() {
        return binary;
    }

    public String diff() {
        return diff;
    }
}
