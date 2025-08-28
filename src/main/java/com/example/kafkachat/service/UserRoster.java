package com.example.kafkachat.service;

import com.example.kafkachat.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final String root = "chat_history";
    private static final History history = new History(Paths.get(root));
    private static volatile UserRoster instance;
    private static final Logger log = LoggerFactory.getLogger(UserRoster.class);
    private final Set<String> users         = ConcurrentHashMap.newKeySet();
    private final Map<String, List<ChatMessage>> roomMessages = new ConcurrentHashMap<>();
    private final Map<String, List<ChatMessage>> userMessages = new ConcurrentHashMap<>();

    public static UserRoster getInstance() {
        if (instance == null) {
            synchronized (UserRoster.class) {
                if (instance == null)
                    instance = new UserRoster();
            }
        }
        return instance;
    }

    private UserRoster() {
        log.info("loading data from {}", Paths.get(root));
        loadAllData();
    }

    /* -------------------- API -------------------- */

    public void register(String username) {
        users.add(username);
    }

    public Collection<String> allUsers() {
        return users;
    }

    public List<ChatMessage> getRoomHistory(String room) {
        return roomMessages.computeIfAbsent(room, history::loadRoomMessages);
    }
    public List<ChatMessage> getDialogHistory(String userA, String userB) {
        return getPrivateHistoryForUser(userA)
                .stream()
                .filter(m -> (userB.equals(m.getSender()) && userA.equals(m.getReceiver())) ||
                        (userA.equals(m.getSender()) && userB.equals(m.getReceiver())))
                .sorted(java.util.Comparator.comparing(ChatMessage::getTimestamp))
                .toList();
    }
    public List<ChatMessage> getPrivateHistoryForUser(String username) {
        return userMessages.computeIfAbsent(username, history::loadUserMessages);
    }

    public void joinRoom(String room, String username) {
        history.saveRoomMessage(room,
                new ChatMessage("System", username + " вошёл", room, null));
        getRoomHistory(room);  // touch & refresh
    }

    public void sendRoomMessage(String room, ChatMessage msg) {
        history.saveRoomMessage(room, msg);
        roomMessages.computeIfAbsent(room, k -> new ArrayList<>()).add(msg);
    }

    public void sendPrivateMessage(String sender, String receiver, String txt) {
        var msg = new ChatMessage(sender, txt, null, receiver);
        history.saveUserMessage(sender, msg);
        history.saveUserMessage(receiver, msg);
        getPrivateHistoryForUser(sender).add(msg);
        getPrivateHistoryForUser(receiver).add(msg);
    }

    private void loadAllData() {
        /* собираем юзеров из /users/*.json */
        Path usersDir = history.root.resolve("users");
        File[] userFiles = usersDir.toFile().listFiles(
                (dir, name) -> name.endsWith(".json"));
        if (userFiles != null) {
            for (File f : userFiles) {
                users.add(f.getName().replace(".json", ""));
            }
            log.debug("loaded {} users from filesystem", users.size());
        }

        Path roomsDir = history.root.resolve("rooms");
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(roomsDir)) {
            for (Path p : ds) {
                String roomName = p.getFileName().toString().replace(".json", "");
                roomMessages.put(roomName, history.loadRoomMessages(roomName));
            } log.debug("loaded {} rooms from filesystem", roomMessages.size());
        } catch (IOException ignore) {}

        for (String u : users) {
            userMessages.put(u, history.loadUserMessages(u));
        }
    }
}