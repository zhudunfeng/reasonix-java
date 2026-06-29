package com.reasonix.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRegistryTest {

    @Test
    void shouldRegisterAndListTools() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new com.reasonix.tool.builtin.ReadFileTool());
        registry.register(new com.reasonix.tool.builtin.WriteFileTool());
        assertThat(registry.getNames()).contains("read_file", "write_file");
    }
}
