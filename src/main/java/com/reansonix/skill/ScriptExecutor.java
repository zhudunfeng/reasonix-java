package com.reansonix.skill;

/**
 * 脚本执行器。
 *
 * * <p>执行 runAs=script 的 Skill 脚本，支持超时控制。
 */
public interface ScriptExecutor {

    /**
     * 执行脚本。
     *
     * @param script 脚本内容
     * @param timeoutSeconds 超时秒数
     * @return 执行结果
     */
    String execute(String script, long timeoutSeconds);
}
