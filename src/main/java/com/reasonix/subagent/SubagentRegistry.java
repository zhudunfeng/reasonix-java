package com.reasonix.subagent;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 子 Agent 注册表。
 *
 * <p>维护可用的子 Agent 定义，支持按名称查询。
 */
@Component
public class SubagentRegistry {

    private final Map<String, SubagentConfig> configs = new ConcurrentHashMap<>();

    public void register(SubagentConfig config) {
        if (config == null || config.getName() == null) {
            return;
        }
        configs.put(config.getName(), config);
    }

    public Optional<SubagentConfig> get(String name) {
        return Optional.ofNullable(configs.get(name));
    }

    public List<SubagentConfig> listAll() {
        return new ArrayList<>(configs.values());
    }
}
