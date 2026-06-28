package com.reansonix.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelFactoryTest {

    private final ModelRegistry modelRegistry = new ModelRegistry();
    private final ModelFactory modelFactory = new ModelFactory(modelRegistry);

    @Test
    void shouldCreateChatModel() {
        modelRegistry.register(new com.reansonix.config.ReasonixConfig.ProviderProperties.ModelDef() {{
            setId("test-model");
        }});
        ChatModel chatModel = modelFactory.createChatModel("test-model");
        assertThat(chatModel).isNotNull();
        assertThat(chatModel.supportsStream()).isFalse();
    }

    @Test
    void shouldThrowOnUnknownModel() {
        assertThat(modelFactory.createChatModel("unknown")).isNull();
    }
}
