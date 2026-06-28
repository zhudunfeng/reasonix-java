package com.reansonix;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 应用启动测试 - 验证 Spring 上下文能否正常加载
 */
@SpringBootTest
class ReansonixApplicationTests {

    @Test
    void contextLoads() {
        // 仅验证上下文成功加载（无空指针异常）
    }
}
