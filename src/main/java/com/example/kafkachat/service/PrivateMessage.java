package com.example.kafkachat.service;

import com.example.kafkachat.kafka.KafkaProducerService;
import com.example.kafkachat.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * КЛАСС PrivateMessage - СЕРВИС ОТПРАВКИ ПРИВАТНЫХ СООБЩЕНИЙ
 *
 * ЧТО ЭТОТ КЛАСС ДЕЛАЕТ:
 * 1. Предоставляет упрощенный интерфейс для отправки приватных сообщений
 * 2. Выполняет валидацию входных параметров перед отправкой
 * 3. Отправляет сообщения асинхронно в отдельном потоке
 * 4. Использует KafkaProducerService для фактической отправки в топик "private-messages"
 * 5. Обеспечивает логирование процесса отправки и обработку ошибок
 * 6. Предоставляет метод для корректного закрытия ресурсов
 *
 * ОСОБЕННОСТИ РЕАЛИЗАЦИИ:
 * - Асинхронная отправка в фоновом потоке для избежания блокировки UI
 * - Валидация параметров для предотвращения отправки некорректных сообщений
 * - Автоматическое создание объекта ChatMessage из простых параметров
 * - Обработка исключений на уровне отправки с детальным логированием
 */
public final class PrivateMessage {

    private static final Logger logger = LoggerFactory.getLogger(PrivateMessage.class);
    private final KafkaProducerService producer;

    public PrivateMessage(KafkaProducerService producer) {
        this.producer = producer;
    }

    /**
     * Отправляет приватное сообщение без createPrivateMessage.
     */
    public void sendPrivateMessage(String sender, String receiver, String content) {
        if (sender == null || receiver == null || content == null || content.isBlank()) {
            logger.warn("Отправка пустого сообщения игнорируется");
            return;
        }

        ChatMessage msg = new ChatMessage(sender, content, null, receiver);

        new Thread(() -> {
            try {
                producer.sendMessage("private-messages", msg);
                logger.info("Приватное сообщение отправлено: {} -> {} : {}", sender, receiver, content);
            } catch (Exception e) {
                logger.error("Ошибка отправки приватного сообщения", e);
            }
        }).start();
    }

    public void close() {
        if (producer != null) {
            producer.close();
            logger.info("KafkaProducerService закрыт");
        }
    }
}