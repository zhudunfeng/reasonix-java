package com.reansonix.subagent;

/**
 * 工作区模式。
 */
public enum WorkspaceMode {

    /**
     * 共享工作区，可直接读写项目文件。
     */
    SHARED,

    /**
     * 隔离工作区，限制在临时目录内执行。
     */
    ISOLATED,

    /**
     * 只读工作区，禁止写操作。
     */
    READ_ONLY
}
