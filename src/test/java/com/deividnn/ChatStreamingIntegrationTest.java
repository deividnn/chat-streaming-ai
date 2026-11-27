package com.deividnn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

/**
 * Testes de integração ponta a ponta (end-to-end) da aplicação.
 *
 * <p>{@code @SpringBootTest} inicializa o contexto completo do Spring Boot (incluindo {@code ChatAiConfig},
 * {@code MessageWindowChatMemory}, {@code ChatClientResolver}, {@code ChatStreamingService} e
 * {@code ChatController}), enquanto {@code @AutoConfigureMockMvc} disponibiliza o {@link MockMvc}.
 * As propriedades inline fornecem chaves fictícias para garantir que a autoconfiguração dos provedores
 * suba em qualquer máquina sem depender de variáveis de ambiente reais.
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.google.genai.api-key=test-gemini-key",
        "spring.ai.ollama.base-url=http://localhost:11434"
})
@AutoConfigureMockMvc
class ChatStreamingIntegrationTest {

    /** Cliente MockMvc conectado ao contexto completo da aplicação. */
    @Autowired
    private MockMvc mockMvc;

    /** Bean real de {@link ChatMemory} ({@code MessageWindowChatMemory}) injetado para limpar estado entre testes. */
    @Autowired
    private ChatMemory chatMemory;

    /** Mock apenas da fronteira externa OpenAI para evitar chamadas HTTP reais durante os testes. */
    @MockitoBean
    private OpenAiChatModel openAiChatModel;

    /** Mock apenas da fronteira externa Google GenAI (Gemini) para testes determinísticos e offline. */
    @MockitoBean
    private GoogleGenAiChatModel googleGenAiChatModel;

    /** Mock apenas da fronteira externa Ollama para não exigir container/serviço Ollama ativo no build. */
    @MockitoBean
    private OllamaChatModel ollamaChatModel;

    /**
     * Antes de cada teste, limpa as conversas usadas nos cenários e configura {@code getOptions()}
     * nos mocks de {@code ChatModel}, pois o {@code ChatClient} do Spring AI chama {@code getOptions().mutate()}
     * ao montar o prompt final.
     */
    @BeforeEach
    void setUp() {
        chatMemory.clear("it-conv-1");
        chatMemory.clear("it-conv-openai");
        chatMemory.clear("it-conv-gemini");

        when(ollamaChatModel.getOptions()).thenReturn(OllamaChatOptions.builder().model("llama3.2").build());
        when(openAiChatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().model("gpt-4o-mini").build());
        when(googleGenAiChatModel.getOptions()).thenReturn(GoogleGenAiChatOptions.builder().model("gemini-2.5-flash").build());
    }

    /**
     * Exercita o ciclo completo integrado:
     * 1) Envia {@code POST /api/chat/stream} sem {@code provider} (deve cair no padrão {@code ollama});
     * 2) Verifica os chunks SSE e o evento {@code done};
     * 3) Consulta {@code GET /api/chat/{id}/history} confirmando que o {@code MessageChatMemoryAdvisor}
     *    persistiu a pergunta do usuário e a resposta concatenada do assistente;
     * 4) Executa {@code DELETE /api/chat/{id}} e confirma que o histórico ficou vazio.
     */
    @Test
    @DisplayName("Fluxo completo: stream com provedor padrão (ollama), consulta de histórico e limpeza de memória")
    void shouldStreamPersistHistoryAndClearConversation() throws Exception {
        when(ollamaChatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("Olá, ")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("tudo bem?"))))
        ));

        MvcResult streamResult = mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "it-conv-1",
                                  "message": "Oi assistente"
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        streamResult.getAsyncResult(5000L);

        MvcResult dispatched = mockMvc.perform(asyncDispatch(streamResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        String body = dispatched.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains("event:message")
                .contains("\"provider\":\"ollama\"")
                .contains("\"content\":\"Olá, \"")
                .contains("\"content\":\"tudo bem?\"")
                .contains("event:done")
                .contains("\"done\":true");

        verify(ollamaChatModel).stream(any(Prompt.class));

        // Confirma que a memória real gravou as 2 mensagens (user + assistant agregada).
        mockMvc.perform(get("/api/chat/it-conv-1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[0].text").value("Oi assistente"))
                .andExpect(jsonPath("$[1].role").value("assistant"))
                .andExpect(jsonPath("$[1].text").value("Olá, tudo bem?"));

        // Limpa a conversa e valida que o histórico não possui mais registros.
        mockMvc.perform(delete("/api/chat/it-conv-1"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/chat/it-conv-1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    /**
     * Verifica de ponta a ponta que informar {@code "provider": "openai"} roteia a chamada
     * para o bean {@code openAiChatClient} / {@code openAiChatModel}.
     */
    @Test
    @DisplayName("Deve rotear requisição explicitamente para o provedor OpenAI")
    void shouldRouteStreamToOpenAiProvider() throws Exception {
        when(openAiChatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("Resposta OpenAI"))))
        ));

        MvcResult streamResult = mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "it-conv-openai",
                                  "message": "Explique SSE",
                                  "provider": "openai"
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        streamResult.getAsyncResult(5000L);

        MvcResult dispatched = mockMvc.perform(asyncDispatch(streamResult))
                .andExpect(status().isOk())
                .andReturn();

        String body = dispatched.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains("\"provider\":\"openai\"")
                .contains("\"content\":\"Resposta OpenAI\"")
                .contains("event:done");

        verify(openAiChatModel).stream(any(Prompt.class));
    }

    /**
     * Verifica de ponta a ponta que o alias {@code "google"} é resolvido para {@code GEMINI}
     * e atendido pelo bean {@code geminiChatClient} / {@code googleGenAiChatModel}.
     */
    @Test
    @DisplayName("Deve rotear requisição explicitamente para o provedor Gemini usando alias")
    void shouldRouteStreamToGeminiProviderUsingAlias() throws Exception {
        when(googleGenAiChatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("Resposta Gemini"))))
        ));

        MvcResult streamResult = mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "it-conv-gemini",
                                  "message": "Explique Spring AI",
                                  "provider": "google"
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        streamResult.getAsyncResult(5000L);

        MvcResult dispatched = mockMvc.perform(asyncDispatch(streamResult))
                .andExpect(status().isOk())
                .andReturn();

        String body = dispatched.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains("\"provider\":\"gemini\"")
                .contains("\"content\":\"Resposta Gemini\"")
                .contains("event:done");

        verify(googleGenAiChatModel).stream(any(Prompt.class));
    }

    /**
     * Valida no contexto integrado que um provedor não suportado retorna HTTP 400
     * com corpo JSON contendo a chave {@code error}.
     */
    @Test
    @DisplayName("Deve retornar 400 Bad Request para provedor não suportado")
    void shouldReturnBadRequestForUnsupportedProvider() throws Exception {
        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "it-conv-invalid",
                                  "message": "Olá",
                                  "provider": "claude"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Provedor não suportado: claude"));
    }

    /**
     * Valida no contexto integrado que um JSON vazio ({@code {}}) é rejeitado pela
     * Bean Validation com HTTP 400 Bad Request.
     */
    @Test
    @DisplayName("Deve retornar 400 Bad Request quando campos obrigatórios forem omitidos")
    void shouldReturnBadRequestForMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
