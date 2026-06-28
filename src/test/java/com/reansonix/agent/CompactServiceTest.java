package com.reansonix.agent.compact;

import com.reansonix.agent.model.Session;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompactServiceTest {

    @Test
    void shouldCompactWhenExceedsRatio() {
        Session session = new Session("s1");
        for (int i = 0; i < 10; i++) {
            session.getHistory().add(new com.reansonix.agent.model.ChatMessage(com.reansonix.agent.model.ChatMessage.Role.USER, "msg-" + i));
        }
        CompactService service = new CompactService();
        boolean shouldCompact = service.shouldCompact(session, 8);
        assertThat(shouldCompact).isTrue();
        service.compact(session);
        assertThat(session.isCompactTriggered()).isTrue();
        assertThat(session.getHistory()).hasSizeLessThanOrEqualTo(5);
    }
}
