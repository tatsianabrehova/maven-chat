package com.example.kafkachat.kafka;

import com.example.kafkachat.model.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * КЛАСС KafkaProducerService - СЕРВИС ОТПРАВКИ СООБЩЕНИЙ В KAFKA
 *
 * ЧТО ЭТОТ КЛАСС ДЕЛАЕТ:
 * 1. Создает и настраивает Kafka Producer для отправки сообщений в указанные топики
 * 2. Сериализует объекты ChatMessage в JSON с помощью Jackson ObjectMapper
 * 3. Обеспечивает как синхронную, так и асинхронную отправку сообщений
 * 4. Настроен для надежной доставки сообщений (acks=all, retries)
 * 5. Логирует процесс отправки и обрабатывает ошибки
 * 6. Предоставляет методы для graceful shutdown и очистки ресурсов
 *
 * ОСОБЕННОСТИ РЕАЛИЗАЦИИ:
 * - Использует асинхронную отправку с callback для обработки результата
 * - Поддерживает отправку приватных сообщений в фоновом режиме
 * - Автоматически сериализует объекты в JSON
 * - Обеспечивает корректное освобождение ресурсов при закрытии
 */
public class KafkaProducerService {
    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Конструктор сервиса продюсера Kafka
     * Инициализирует и настраивает Kafka Producer с параметрами надежности
     */
    public KafkaProducerService() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all"); // Гарантированная доставка
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000); // Пауза между повторами
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30000); // Таймаут доставки

        this.producer = new KafkaProducer<>(props);
        logger.info("KafkaProducerService инициализирован");
    }

    /**
     * Отправка сообщения в указанный топик
     * @param topic целевой топик Kafka
     * @param message объект сообщения для отправки
     */
    public void sendMessage(String topic, ChatMessage message) {
        try {
            String jsonMessage = mapper.writeValueAsString(message);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, message.getSender(), jsonMessage);

            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    logger.error("Ошибка при отправке сообщения в топик {}: {}", topic, exception.getMessage());
                } else {
                    logger.debug("Сообщение успешно отправлено в топик {}, partition {}, offset {}",
                            metadata.topic(), metadata.partition(), metadata.offset());
                }
            });

            logger.info("Отправлено сообщение в {}: {} -> {}: {}",
                    topic,
                    message.getSender(),
                    message.getReceiver() != null ? message.getReceiver() : "<все>",
                    message.getContent());
        } catch (Exception e) {
            logger.error("Ошибка при сериализации или отправке сообщения", e);
        }
    }

    /**
     * Асинхронная отправка приватного сообщения в фоновом режиме
     * @param message приватное сообщение для отправки
     */
    public void sendPrivateMessageAsync(ChatMessage message) {
        CompletableFuture.runAsync(() -> {
            logger.debug("Асинхронная отправка приватного сообщения от {} к {}",
                    message.getSender(), message.getReceiver());
            sendMessage("private-messages", message);
        });
    }

    /**
     * Корректное закрытие продюсера с гарантированной отправкой всех сообщений
     */
    public void close() {
        if (producer != null) {
            try {
                producer.flush(); // Гарантированная отправка всех сообщений
                producer.close(); // Корректное освобождение ресурсов
                logger.info("KafkaProducerService закрыт");
            } catch (Exception e) {
                logger.warn("Ошибка при закрытии продюсера", e);
            }
        }
    }
}