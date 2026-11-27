package com.deividnn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principal (ponto de entrada) da aplicação Spring Boot.
 *
 * <p>{@code @SpringBootApplication} combina {@code @Configuration}, {@code @EnableAutoConfiguration}
 * e {@code @ComponentScan}, permitindo que o Spring descubra automaticamente os beans deste pacote
 * e subpacotes e autoconfigure Web MVC e provedores do Spring AI.
 */
@SpringBootApplication
public class ChatStreamingApplication {

    /**
     * Método de entrada da JVM responsável por inicializar o contexto Spring e subir o servidor HTTP embutido.
     *
     * @param args argumentos opcionais de linha de comando (ex.: overrides de propriedades {@code --server.port=8081})
     */
    public static void main(String[] args) {
        SpringApplication.run(ChatStreamingApplication.class, args);
    }
}
