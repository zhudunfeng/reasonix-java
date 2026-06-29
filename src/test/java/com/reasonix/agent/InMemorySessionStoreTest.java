package com.reasonix.agent;

import com.reasonix.agent.store.InMemorySessionStore;
import com.reasonix.agent.store.SessionStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySessionStoreTest {

    @Test
    void shouldCreateAndGetSession() {
        SessionStore store = new InMemorySessionStore();
        var session = store.create("session-1");
        assertThat(session.getSessionId()).isEqualTo("session-1");
        assertThat(store.get("session-1")).isPresent();
    }

    @Test
    void shouldDeleteSession() {
        SessionStore store = new InMemorySessionStore();
        store.create("session-1");
        store.delete("session-1");
        assertThat(store.get("session-1")).isEmpty();
    }
}
