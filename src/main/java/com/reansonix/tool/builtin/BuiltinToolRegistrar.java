package com.reansonix.tool.builtin;

import com.reansonix.tool.Tool;
import com.reansonix.tool.ToolRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内置工具自动注册器 - Spring Boot 启动时自动注册所有内置 Tool
 */
@Component
public class BuiltinToolRegistrar {

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
}
