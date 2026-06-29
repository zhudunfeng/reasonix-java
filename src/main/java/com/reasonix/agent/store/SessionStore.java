package com.reasonix.agent.store;

import com.reasonix.agent.model.Session;

import java.util.List;
import java.util.Optional;

/**
 * Session 仓储接口。
 */
public interface SessionStore {

    Session create(String sessionId);

    Optional<Session> get(String sessionId);

    List<Session> listAll();

    void save(Session session);

    void delete(String sessionId);
}
