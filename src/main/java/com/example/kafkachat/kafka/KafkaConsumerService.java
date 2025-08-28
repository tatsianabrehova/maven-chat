package com.example.kafkachat.kafka;

import com.example.kafkachat.model.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.swing.*; // ★★★ ДОБАВЛЕН ИМПОРТ ★★★
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * КЛАСС KafkaConsumerService - СЕРВИС ПОТРЕБЛЕНИЯ СООБЩЕНИЙ ИЗ KAFKA
 *
 * ЧТО ЭТОТ КЛАСС ДЕЛАЕТ:
 * 1. Создает и настраивает Kafka Consumer для чтения сообщений из указанного топика
 * 2. Десериализует JSON-сообщения в объекты ChatMessage с помощью Jackson ObjectMapper
 * 3. Передает полученные сообщения обработчику через Consumer<ChatMessage> callback
 * 4. Работает в отдельном фоновом потоке для непрерывного опроса Kafka
 * 5. Обеспечивает корректное завершение работы при вызове shutdown()
 * 6. Логирует ошибки десериализации и процесс потребления сообщений
 *
 * ОСОБЕННОСТИ РЕАЛИЗАЦИИ:
 * - Использует демон-поток для фоновой обработки сообщений
 * - Поддерживает graceful shutdown через флаг running
 * - Обрабатывает сообщения в бесконечном цикле с poll()-таймаутом
 * - Автоматически подписывается на указанный топик при создании
 * - Настроен на чтение новых сообщений (latest)
 */

public class KafkaConsumerService {
    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);
    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile boolean running = true;
    private final Thread consumerThread;
    private final Consumer<ChatMessage> messageHandler;

    /**
     * Конструктор сервиса потребителя Kafka
     * @param topic топик для подписки
     * @param messageHandler обработчик полученных сообщений
     */
    public KafkaConsumerService(String topic, Consumer<ChatMessage> messageHandler) {
        this.messageHandler = messageHandler;

        Properties props = new Properties();
        //props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "host.docker.internal:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "chat-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1");
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "1");
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, "100");

        this.consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(topic));

        consumerThread = new Thread(() -> {
            try {
                while (running) {
                    try {
                        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(50));
                        System.out.println("📥 KafkaConsumer: poll() вернул " + records.count() + " сообщений из топика");
                        if (records.count() > 0) {
                            logger.debug("Processing {} messages", records.count());

                            for (ConsumerRecord<String, String> record : records) {
                                try {
                                    ChatMessage message = mapper.readValue(record.value(), ChatMessage.class);

                                    SwingUtilities.invokeLater(() -> {
                                        messageHandler.accept(message);
                                    });

                                } catch (Exception e) {
                                    logger.error("Ошибка при десериализации сообщения", e);
                                }
                            }

                            consumer.commitSync();
                        }

                    } catch (WakeupException e) {
                        // Ожидаемое исключение при wakeup()
                        if (!running) break;
                    } catch (Exception e) {
                        if (running) {
                            logger.error("Unexpected error in Kafka consumer loop", e);
                        }
                    }
                }
            } finally {
                consumer.close();
                logger.info("KafkaConsumer остановлен и закрыт");
            }
        });

        consumerThread.setDaemon(true);
        consumerThread.start();
        logger.info("KafkaConsumerService started for topic: {}", topic);
    }

    public void shutdown() {
        running = false;
        consumer.wakeup(); // ★★★ БУДИМ CONSUMER ДЛЯ БЫСТРОГО ВЫХОДА ★★★
        try {
            consumerThread.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void wakeup() {
        consumer.wakeup();
    }
}