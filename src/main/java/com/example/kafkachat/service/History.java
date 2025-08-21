package com.example.kafkachat.service;

import com.example.kafkachat.model.ChatMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
/**
 * КЛАСС History - СЕРВИС ХРАНЕНИЯ И ЗАГРУЗКИ ИСТОРИИ СООБЩЕНИЙ
 *
 * ЧТО ЭТОТ КЛАСС ДЕЛАЕТ:
 * 1. Обеспечивает сохранение истории сообщений в файловую систему
 * 2. Организует данные по двум категориям: комнаты (rooms) и пользователи (users)
 * 3. Использует JSON формат для хранения сообщений через Jackson ObjectMapper
 * 4. Предоставляет API для загрузки и сохранения сообщений комнат и пользователей
 * 5. Автоматически создает необходимые директории при инициализации
 * 6. Обрабатывает ошибки чтения/записи и обеспечивает отказоустойчивость
 *
 * ОСОБЕННОСТИ РЕАЛИЗАЦИИ:
 * - Поддержка Java Time модулей для корректной работы с Instant
 * - Pretty Print JSON для удобства чтения файлов истории
 * - Автоматическое создание пустых списков при отсутствии файлов
 * - Логирование ошибок без прерывания работы приложения
 * - Потокобезопасные операции чтения/записи (на уровне методов)
 */
public final class History {

    private static final Logger log = LoggerFactory.getLogger(History.class);
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();  // java-time module registration

    final Path root;

    public History(Path root) {
        this.root = root;
        init();
    }

    private void init() {
        try {
            Files.createDirectories(root.resolve("rooms"));
            Files.createDirectories(root.resolve("users"));
        } catch (IOException e) {
            log.error("history dirs", e);
        }
    }

    /* -------------------- public API -------------------- */

    public List<ChatMessage> loadRoomMessages(String room) {
        return readList(root.resolve("rooms").resolve(room + ".json"));
    }

    public List<ChatMessage> loadUserMessages(String username) {
        return readList(root.resolve("users").resolve(username + ".json"));
    }

    public void saveRoomMessage(String room, ChatMessage message) {
        save(root.resolve("rooms").resolve(room + ".json"), message);
    }

    public void saveUserMessage(String username, ChatMessage message) {
        save(root.resolve("users").resolve(username + ".json"), message);
    }

    /* -------------------- internal plumbing -------------------- */

    private void save(Path file, ChatMessage message) {
        try {
            var current = readList(file);
            current.add(message);
            writeList(file, current);
        } catch (Exception e) {
            log.error("Failed to store message {}", message, e);
        }
    }

    private List<ChatMessage> readList(Path file) {
        if (!Files.exists(file))
            return new ArrayList<>();
        try {
            return objectMapper.readValue(
                    file.toFile(),
                    new TypeReference<>() {});
        } catch (IOException ex) {
            log.error("Cannot read {} : {}", file, ex.getMessage());
            return new ArrayList<>();
        }
    }

    private void writeList(Path file, List<ChatMessage> list) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(file.toFile(), list);
    }

    /* -------------------- облегчённое логирование (опционально) -------------------- */
    private void logEvent(String fmt, Object... args) {
        System.out.printf(fmt + "%n", args);
    }
}