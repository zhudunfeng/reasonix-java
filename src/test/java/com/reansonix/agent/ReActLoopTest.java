package com.reansonix.agent;

import com.reansonix.agent.loop.ReActLoop;
import com.reansonix.agent.model.Session;
import com.reansonix.agent.store.InMemorySessionStore;
import com.reansonix.agent.store.SessionStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReActLoopTest {

    @Test
    void shouldReturnQueryWhenNoToolsCalled() {
        SessionStore sessionStore = new InMemorySessionStore();
        ReActLoop loop = new ReActLoop(new com.reansonix.provider.OpenAiCompatibleChatModel("test"), sessionStore, null, null);
        String result = loop.execute("test-session", "hello");
        assertThat(result).isEqualTo("hello");
    }
}
