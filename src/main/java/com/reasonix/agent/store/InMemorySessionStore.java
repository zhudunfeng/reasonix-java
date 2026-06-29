package com.reasonix.agent.store;

import com.reasonix.agent.model.Session;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认内存 Session 存储。
 *
 * <p>可替换为 Redis / JDBC 实现。
 */
@Component
public class InMemorySessionStore implements SessionStore {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    @Override
    public Session create(String sessionId) {
        Session session = new Session(sessionId);
        sessions.put(sessionId, session);
        return session;
    }

    @Override
    public Optional<Session> get(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public List<Session> listAll() {
        return new ArrayList<>(sessions.values());
    }

    @Override
    public void save(Session session) {
        if (session != null && session.getSessionId() != null) {
            sessions.put(session.getSessionId(), session);
        }
    }

    @Override
    public void delete(String sessionId) {
        sessions.remove(sessionId);
    }
}
