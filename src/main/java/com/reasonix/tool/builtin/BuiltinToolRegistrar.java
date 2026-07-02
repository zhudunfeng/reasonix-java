package com.reasonix.tool.builtin;

import com.reasonix.tool.ToolRegistry;
import org.springframework.stereotype.Component;

/**
 * 内置工具自动注册器 - Spring Boot 启动时自动注册所有内置 Tool
 */
@Component
public class BuiltinToolRegistrar {

    private final ToolRegistry registry;

    public BuiltinToolRegistrar(ToolRegistry registry,
                                BashTool bashTool,
                                ReadFileTool readFileTool,
                                WriteFileTool writeFileTool,
                                EditFileTool editFileTool,
                                GrepTool grepTool,
                                GlobTool globTool,
                                LsTool lsTool,
                                WebFetchTool webFetchTool,
                                TodoWriteTool todoWriteTool) {
        this.registry = registry;
        registry.register(bashTool);
        registry.register(readFileTool);
        registry.register(writeFileTool);
        registry.register(editFileTool);
        registry.register(grepTool);
        registry.register(globTool);
        registry.register(lsTool);
        registry.register(webFetchTool);
        registry.register(todoWriteTool);
    }

    public ToolRegistry getRegistry() {
        return registry;
    }
}
