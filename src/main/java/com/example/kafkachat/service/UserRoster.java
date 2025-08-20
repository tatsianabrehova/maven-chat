package com.example.kafkachat.service;

import com.example.kafkachat.model.ChatMessage;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * КЛАСС UserRoster - СЕРВИС УПРАВЛЕНИЯ ПОЛЬЗОВАТЕЛЯМИ И ИСТОРИЕЙ СООБЩЕНИЙ
 *
 * ЧТО ЭТОТ КЛАСС ДЕЛАЕТ:
 * 1. Реализует паттерн Singleton для глобального доступа к данным пользователей
 * 2. Управляет регистрацией и хранением списка активных пользователей
 * 3. Обеспечивает загрузку и сохранение истории сообщений комнат и приватных чатов
 * 4. Предоставляет API для работы с историей сообщений
 * 5. Синхронизирует данные с файловой системой через History сервис
 * 6. Обеспечивает потокобезопасность через Concurrent коллекции
 *
 * ОСОБЕННОСТИ РЕАЛИЗАЦИИ:
 * - Double-Checked Locking для thread-safe Singleton
 * - Lazy loading истории сообщений при первом обращении
 * - Автоматическая загрузка существующих данных при инициализации
 * - Поддержка как комнатных, так и приватных сообщений
 */
public final class UserRoster {

    private static final String ROOT = "chat_history";
    private static final History history = new History(Paths.get(ROOT));
    private static volatile UserRoster instance;

    private final Set<String> users = ConcurrentHashMap.newKeySet();
    private final Map<String, List<ChatMessage>> roomMessages = new ConcurrentHashMap<>();
    private final Map<String, List<ChatMessage>> userMessages = new ConcurrentHashMap<>();

    /**
     * Получение экземпляра Singleton
     * @return единственный экземпляр UserRoster
     */
    public static UserRoster getInstance() {
        if (instance == null) {
            synchronized (UserRoster.class) {
                if (instance == null) {
                    instance = new UserRoster();
                }
            }
        }
        return instance;
    }

    private UserRoster() {
        loadAllData();
    }

    /**
     * Регистрация нового пользователя
     * @param username имя пользователя для регистрации
     */
    public void register(String username) {
        users.add(username);
    }

    /**
     * Получение списка всех зарегистрированных пользователей
     * @return коллекция имен пользователей
     */
    public Collection<String> allUsers() {
        return Collections.unmodifiableCollection(users);
    }

    /**
     * Получение истории сообщений комнаты
     * @param room название комнаты
     * @return список сообщений комнаты
     */
    public List<ChatMessage> getRoomHistory(String room) {
        return roomMessages.computeIfAbsent(room, history::loadRoomMessages);
    }

    /**
     * Получение истории приватных сообщений пользователя
     * @param username имя пользователя
     * @return список приватных сообщений
     */
    public List<ChatMessage> getPrivateHistoryForUser(String username) {
        return userMessages.computeIfAbsent(username, history::loadUserMessages);
    }

    /**
     * Получение истории диалога между двумя пользователями
     * @param userA первый пользователь
     * @param userB второй пользователь
     * @return отсортированный по времени список сообщений
     */
    public List<ChatMessage> getDialogHistory(String userA, String userB) {
        return getPrivateHistoryForUser(userA)
                .stream()
                .filter(m -> (userB.equals(m.getSender()) && userA.equals(m.getReceiver())) ||
                        (userA.equals(m.getSender()) && userB.equals(m.getReceiver())))
                .sorted(Comparator.comparing(ChatMessage::getTimestamp))
                .toList();
    }

    /**
     * Обработка входа пользователя в комнату
     * @param room название комнаты
     * @param username имя пользователя
     */
    public void joinRoom(String room, String username) {
        ChatMessage sysMsg = new ChatMessage("System", username + " вошёл", room, null);
        history.saveRoomMessage(room, sysMsg);
        getRoomHistory(room).add(sysMsg); // вот этого не хватало
    }


    /**
     * Отправка сообщения в комнату
     * @param room название комнаты
     * @param msg сообщение
     */
    public void sendRoomMessage(String room, ChatMessage msg) {
        history.saveRoomMessage(room, msg);
        getRoomHistory(room).add(msg);
    }

    /**
     * Отправка приватного сообщения
     * @param sender отправитель
     * @param receiver получатель
     * @param txt текст сообщения
     */
    public void sendPrivateMessage(String sender, String receiver, String txt) {
        var msg = new ChatMessage(sender, txt, null, receiver);
        history.saveUserMessage(sender, msg);
        history.saveUserMessage(receiver, msg);
        getPrivateHistoryForUser(sender).add(msg);
        getPrivateHistoryForUser(receiver).add(msg);
    }

    /**
     * Загрузка всех данных при инициализации
     */
    private void loadAllData() {
        loadUsersFromFiles();
        loadRoomHistory();
        loadUserHistory();
    }

    /**
     * Загрузка списка пользователей из файлов
     */
    private void loadUsersFromFiles() {
        Path usersDir = history.root.resolve("users");
        File[] userFiles = usersDir.toFile().listFiles(
                (dir, name) -> name.endsWith(".json"));
        if (userFiles != null) {
            Arrays.stream(userFiles)
                    .map(f -> f.getName().replace(".json", ""))
                    .forEach(users::add);
        }
    }

    /**
     * Загрузка истории комнат
     */
    private void loadRoomHistory() {
        Path roomsDir = history.root.resolve("rooms");
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(roomsDir)) {
            ds.forEach(p -> {
                String roomName = p.getFileName().toString().replace(".json", "");
                roomMessages.put(roomName, history.loadRoomMessages(roomName));
            });
        } catch (IOException ignore) {
        }
    }

    /**
     * Загрузка истории пользователей
     */
    private void loadUserHistory() {
        users.forEach(u -> userMessages.put(u, history.loadUserMessages(u)));
    }
}