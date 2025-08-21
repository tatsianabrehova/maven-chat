package com.example.kafkachat.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
/**
 * КЛАСС ChatMessage - МОДЕЛЬ СООБЩЕНИЯ ЧАТА
 *
 * ЧТО ЭТОТ КЛАСС ДЕЛАЕТ:
 * 1. Представляет структуру сообщения чата с основными полями:
 *    - Уникальный идентификатор (UUID)
 *    - Отправитель сообщения
 *    - Текст сообщения
 *    - Временная метка создания
 *    - Комната/канал назначения
 *    - Получатель (для приватных сообщений)
 * 2. Обеспечивает сериализацию/десериализацию JSON через Jackson
 * 3. Предоставляет удобные конструкторы для создания сообщений
 * 4. Реализует equals/hashCode на основе ID для корректного сравнения
 * 5. Включает методы для безопасного доступа и модификации полей
 *
 * ОСОБЕННОСТИ РЕАЛИЗАЦИИ:
 * - Автоматическая генерация UUID и временной метки при создании
 * - Поддержка как публичных, так и приватных сообщений
 * - Jackson-аннотации для корректной работы с JSON
 */
public class ChatMessage {

    private UUID    id;
    private String  sender;
    private String  content;
    private Instant timestamp;
    private String  room;
    private String  receiver;

    /** Для Jackson */
    public ChatMessage() {}

    /** “Красивый” конструктор для остального кода */
    public ChatMessage(String sender,
                       String content,
                       String room,
                       String receiver) {

        this.id        = UUID.randomUUID();
        this.sender    = sender;
        this.content   = content;
        this.timestamp = Instant.now();
        this.room      = room;
        this.receiver  = receiver;
    }

    /* ------------------------------------ getters / setters ------------------------------------ */

    public UUID    getId()        { return id; }
    public String  getSender()    { return sender; }
    public String  getContent()   { return content; }
    public Instant getTimestamp() { return timestamp; }
    public String  getRoom()      { return room; }
    public String  getReceiver()  { return receiver; }

    public void setId(UUID id)               { this.id = id; }
    public void setSender(String sender)     { this.sender = sender; }
    public void setContent(String content)   { this.content = content; }
    public void setTimestamp(Instant t)      { this.timestamp = t; }
    public void setRoom(String room)         { this.room = room; }
    public void setReceiver(String receiver) { this.receiver = receiver; }

    /* ------------------------------------ helpers ------------------------------------ */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatMessage that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "id="        + id +
                ", sender='" + sender + '\'' +
                ", content='" + content + '\'' +
                ", timestamp=" + timestamp +
                ", room='"   + room   + '\'' +
                ", receiver='" + receiver + '\'' +
                '}';
    }
}