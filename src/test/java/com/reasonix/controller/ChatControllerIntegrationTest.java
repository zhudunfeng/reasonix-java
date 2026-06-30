package com.reasonix.controller;

import com.reasonix.agent.AgentController;
import com.reasonix.agent.AgentResult;
import com.reasonix.config.ReasonixConfig;
import com.reasonix.provider.ModelRegistry;
import com.reasonix.provider.ModelResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ChatControllerIntegrationTest {

    private final MockMvc mockMvc;

    ChatControllerIntegrationTest(ChatController chatController) {
        this.mockMvc = MockMvcBuilders.standaloneSetup(chatController).build();
    }

    @Test
    void shouldReturnBadRequestForEmptyQuery() throws Exception {
        mockMvc.perform(post("/api/chat")
                .contentType("application/json")
                .content("{\"query\":\"   \"}"))
            .andExpect(status().isOk());
    }
}
