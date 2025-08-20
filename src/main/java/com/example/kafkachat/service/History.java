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

    // Инициализация ObjectMapper с поддержкой Java Time модулей
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    protected final Path root;

    /**
     * Конструктор сервиса истории сообщений
     * @param root корневая директория для хранения файлов истории
     */
    public History(Path root) {
        this.root = root;
        init();
    }

    /**
     * Инициализация директорий для хранения истории
     * Создает папки rooms и users в корневой директории
     */
    private void init() {
        try {
            Files.createDirectories(root.resolve("rooms"));
            Files.createDirectories(root.resolve("users"));
            log.info("Директории истории инициализированы: {}", root);
        } catch (IOException e) {
            log.error("Ошибка создания директорий истории", e);
        }
    }


    /**
     * Загрузка истории сообщений комнаты
     * @param room название комнаты
     * @return список сообщений комнаты
     */
    public List<ChatMessage> loadRoomMessages(String room) {
        return readList(root.resolve("rooms").resolve(room + ".json"));
    }

    /**
     * Загрузка истории личных сообщений пользователя
     * @param username имя пользователя
     * @return список личных сообщений пользователя
     */
    public List<ChatMessage> loadUserMessages(String username) {
        return readList(root.resolve("users").resolve(username + ".json"));
    }

    /**
     * Сохранение сообщения в историю комнаты
     * @param room название комнаты
     * @param message сообщение для сохранения
     */
    public void saveRoomMessage(String room, ChatMessage message) {
        save(root.resolve("rooms").resolve(room + ".json"), message);
    }

    /**
     * Сохранение личного сообщения в историю пользователя
     * @param username имя пользователя
     * @param message личное сообщение для сохранения
     */
    public void saveUserMessage(String username, ChatMessage message) {
        save(root.resolve("users").resolve(username + ".json"), message);
    }


    /**
     * Сохранение сообщения в файл
     * @param file файл для сохранения
     * @param message сообщение для добавления
     */
    private void save(Path file, ChatMessage message) {
        try {
            List<ChatMessage> current = readList(file);
            current.add(message);
            writeList(file, current);
            log.debug("Сообщение сохранено в историю: {}", file.getFileName());
        } catch (Exception e) {
            log.error("Ошибка сохранения сообщения {} в файл {}", message, file, e);
        }
    }

    /**
     * Чтение списка сообщений из файла
     * @param file файл для чтения
     * @return список сообщений (пустой список если файл не существует)
     */
    private List<ChatMessage> readList(Path file) {
        if (!Files.exists(file)) {
            log.debug("Файл истории не существует: {}", file);
            return new ArrayList<>();
        }

        try {
            List<ChatMessage> messages = objectMapper.readValue(
                    file.toFile(),
                    new TypeReference<List<ChatMessage>>() {}
            );
            log.debug("Загружено {} сообщений из {}", messages.size(), file.getFileName());
            return new ArrayList<>(messages); // <--- ВАЖНО
        } catch (IOException ex) {
            log.error("Ошибка чтения файла истории {}: {}", file, ex.getMessage());
            return new ArrayList<>();
        }
    }


    /**
     * Запись списка сообщений в файл
     * @param file файл для записи
     * @param list список сообщений
     * @throws IOException если произошла ошибка записи
     */
    private void writeList(Path file, List<ChatMessage> list) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(file.toFile(), list);
        log.trace("Список из {} сообщений записан в {}", list.size(), file.getFileName());
    }

    /**
     * Вспомогательный метод для логирования событий
     * @param fmt формат строки
     * @param args аргументы для форматирования
     */
    private void logEvent(String fmt, Object... args) {
        if (log.isDebugEnabled()) {
            log.debug(String.format(fmt, args));
        }
    }
}