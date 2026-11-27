package com.deividnn.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
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

import com.deividnn.dto.ChatRequest;
import com.deividnn.dto.ChatStreamChunk;
import com.deividnn.service.ChatStreamingService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Testes unitários da camada web (slice test) para {@link ChatController}.
 *
 * <p>{@code @WebMvcTest(ChatController.class)} carrega apenas o DispatcherServlet, serialização JSON,
 * Bean Validation e o controller informado (sem subir Spring AI ou modelos), tornando os testes
 * HTTP rápidos e focados no contrato REST.
 */
@WebMvcTest(ChatController.class)
class ChatControllerTest {

    /** Cliente simulado do Spring MVC usado para disparar requisições HTTP em memória sem abrir porta TCP. */
    @Autowired
    private MockMvc mockMvc;

    /**
     * Mock gerenciado pelo Spring Test ({@code @MockitoBean}) que substitui o bean real de
     * {@link ChatStreamingService} dentro do contexto slice.
     */
    @MockitoBean
    private ChatStreamingService chatStreamingService;

    /**
     * Valida o contrato SSE de {@code POST /api/chat/stream}:
     * inicia processamento assíncrono, aguarda o encerramento do {@link SseEmitter} e verifica
     * o Content-Type {@code text/event-stream} e os eventos {@code message} e {@code done}.
     */
    @Test
    @DisplayName("POST /api/chat/stream deve retornar eventos SSE válidos")
    void shouldStreamChatEvents() throws Exception {
        SseEmitter emitter = new SseEmitter(0L);
        when(chatStreamingService.streamChat(any(ChatRequest.class))).thenAnswer(invocation -> {
            // Simula o envio assíncrono em uma Virtual Thread, reproduzindo o comportamento real do serviço.
            Thread.startVirtualThread(() -> {
                try {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(new ChatStreamChunk("conv-1", "ollama", "Olá!", false), MediaType.APPLICATION_JSON));
                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data(new ChatStreamChunk("conv-1", "ollama", "", true), MediaType.APPLICATION_JSON));
                    emitter.complete();
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            });
            return emitter;
        });

        MvcResult mvcResult = mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "conv-1",
                                  "message": "Oi",
                                  "provider": "ollama"
                                }
                                """))
                // Confirma que o retorno SseEmitter iniciou um ciclo assíncrono no Servlet.
                .andExpect(request().asyncStarted())
                .andReturn();

        // Aguarda até 5000ms pelo término da Virtual Thread porque SseEmitter(0L) usa timeout 0 no MockMvc.
        mvcResult.getAsyncResult(5000L);

        // Executa o dispatch final da resposta assíncrona para coletar o corpo SSE completo.
        MvcResult dispatched = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        // Lê explicitamente em UTF-8 para validar corretamente caracteres acentuados ("Olá!").
        String body = dispatched.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains("event:message")
                .contains("\"content\":\"Olá!\"")
                .contains("event:done")
                .contains("\"done\":true");
    }

    /**
     * Garante que {@code @Valid} + {@code @NotBlank} no {@link ChatRequest} rejeitem payloads
     * com campos vazios ou apenas espaços retornando HTTP {@code 400 Bad Request}.
     */
    @Test
    @DisplayName("POST /api/chat/stream deve retornar 400 quando conversationId ou message estiverem em branco")
    void shouldReturn400WhenRequestValidationFails() throws Exception {
        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "",
                                  "message": "   "
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    /**
     * Valida que o {@code @ExceptionHandler(IllegalArgumentException.class)} converte erros
     * de provedor inválido em HTTP 400 com o JSON {@code {"error": "..."}}.
     */
    @Test
    @DisplayName("POST /api/chat/stream deve retornar 400 com mensagem de erro quando provedor for inválido")
    void shouldReturn400WhenProviderIsUnsupported() throws Exception {
        when(chatStreamingService.streamChat(any(ChatRequest.class)))
                .thenThrow(new IllegalArgumentException("Provedor não suportado: invalid"));

        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "conv-1",
                                  "message": "Oi",
                                  "provider": "invalid"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Provedor não suportado: invalid"));
    }

    /**
     * Verifica que {@code GET /api/chat/{conversationId}/history} transforma corretamente
     * {@link UserMessage} e {@link AssistantMessage} em objetos JSON com campos {@code role} e {@code text}.
     */
    @Test
    @DisplayName("GET /api/chat/{conversationId}/history deve retornar lista formatada com role e text")
    void shouldReturnFormattedHistory() throws Exception {
        when(chatStreamingService.getHistory("conv-42")).thenReturn(List.of(
                new UserMessage("Qual é a capital do Brasil?"),
                new AssistantMessage("Brasília.")
        ));

        mockMvc.perform(get("/api/chat/conv-42/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[0].text").value("Qual é a capital do Brasil?"))
                .andExpect(jsonPath("$[1].role").value("assistant"))
                .andExpect(jsonPath("$[1].text").value("Brasília."));
    }

    /**
     * Valida que {@code DELETE /api/chat/{conversationId}} aciona {@code clearHistory} no serviço
     * e responde com HTTP {@code 204 No Content}.
     */
    @Test
    @DisplayName("DELETE /api/chat/{conversationId} deve limpar histórico e retornar 204")
    void shouldClearHistoryAndReturnNoContent() throws Exception {
        doNothing().when(chatStreamingService).clearHistory("conv-42");

        mockMvc.perform(delete("/api/chat/conv-42"))
                .andExpect(status().isNoContent());

        verify(chatStreamingService).clearHistory("conv-42");
    }
}
