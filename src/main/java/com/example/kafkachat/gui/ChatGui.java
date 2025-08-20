package com.example.kafkachat.gui;

import com.example.kafkachat.kafka.KafkaConsumerService;
import com.example.kafkachat.kafka.KafkaProducerService;
import com.example.kafkachat.model.ChatMessage;
import com.example.kafkachat.service.UserRoster;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * Основное окно чата (ChatGui).
 *
 * Назначение:
 * - Отображает сообщения в выбранной комнате (room).
 * - Позволяет отправлять публичные сообщения.
 * - Отображает список пользователей.
 * - Поддерживает открытие приватного чата.
 *
 * Важные моменты:
 * - Сообщения хранятся в UserRoster (локальная история в памяти).
 * - Дубли убраны через проверку в UserRoster.
 * - Подписки Kafka:
 *   → "chat-room-<room>"  для сообщений этой комнаты.
 *   → "private-messages"  для личных сообщений.
 */
public class ChatGui extends JFrame {

    private final KafkaProducerService producer;
    private final KafkaConsumerService publicConsumer;
    private final KafkaConsumerService privateConsumer;
    private final UserRoster userRoster;

    private final JTextField nicknameField = new JTextField(15);
    private final JTextArea  messageArea   = new JTextArea(20, 50);
    private final JTextField inputField    = new JTextField(40);
    private final JButton    sendButton    = new JButton("Send");

    private final DefaultListModel<String> userListModel = new DefaultListModel<>();
    private final JList<String>            userList      = new JList<>(userListModel);

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final String room;
    public ChatGui(String nickname, String room) {
        this.producer    = new KafkaProducerService();
        this.room        = room;
        this.userRoster  = UserRoster.getInstance();

        setTitle("Kafka Chat – Room: " + room);
        setSize(800, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) { shutdown(); }
        });

        SwingUtilities.invokeLater(() -> {
            createComponents();
            this.nicknameField.setText(nickname);
            bindEvents();
            loadHistory();
            refreshUsers();
            userRoster.joinRoom(room, nickname);
            setVisible(true);
        });

        publicConsumer  = new KafkaConsumerService("chat-room-" + room,  this::handleMessage);
        privateConsumer = new KafkaConsumerService("private-messages",   this::handleMessage);
    }

    private void createComponents() {
        messageArea.setEditable(false);
        messageArea.setFont(new Font("Dialog", Font.PLAIN, 14));
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        setLayout(new BorderLayout(5, 5));

        JPanel top = new JPanel(new FlowLayout());
        top.add(new JLabel("Nickname:"));
        top.add(nicknameField);

        JPanel right = new JPanel(new BorderLayout());
        right.setPreferredSize(new Dimension(150, 200));
        right.add(new JLabel("Users"), BorderLayout.NORTH);
        right.add(new JScrollPane(userList), BorderLayout.CENTER);
        JButton privBtn = new JButton("Private Chat");
        privBtn.addActionListener(e -> openPrivateChat());
        right.add(privBtn, BorderLayout.SOUTH);

        add(top,                         BorderLayout.NORTH);
        add(new JScrollPane(messageArea), BorderLayout.CENTER);
        add(right,                        BorderLayout.EAST);

        JPanel bottom = new JPanel(new FlowLayout());
        bottom.add(inputField);
        bottom.add(sendButton);
        add(bottom, BorderLayout.SOUTH);
    }

    /* ------------------ event bindings ------------------ */
    private void bindEvents() {
        Runnable send = () -> {
            String nick = nicknameField.getText().trim();
            String text = inputField.getText().trim();
            if (!nick.isEmpty() && !text.isEmpty()) {
                ChatMessage msg = new ChatMessage(nick, text, room, null);
                producer.sendMessage("chat-room-" + room, msg);
                userRoster.sendRoomMessage(room, msg);
                display(msg);
                inputField.setText("");
            }
        };
        sendButton.addActionListener(e -> send.run());
        inputField.addActionListener(e -> send.run());
        nicknameField.addActionListener(e -> refreshUsers());
    }

    private void handleMessage(ChatMessage msg) {
        if ("System".equals(msg.getSender())) return;
        SwingUtilities.invokeLater(() -> {
            userRoster.sendRoomMessage(room, msg); // сохраняем в историю
            display(msg);
        });
    }


    private void loadHistory() {
        userRoster.getRoomHistory(room).forEach(this::display);
    }

    private void refreshUsers() {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            userRoster.allUsers().forEach(userListModel::addElement);
        });
    }

    private void openPrivateChat() {
        String receiver = userList.getSelectedValue();
        if (receiver == null) {
            JOptionPane.showMessageDialog(this, "Выберите пользователя.");
            return;
        }
        new PrivateChatGui(producer, nicknameField.getText().trim(), receiver, userRoster);
    }

    private void display(ChatMessage msg) {
        String line;
        String nick = nicknameField.getText().trim();
        if (msg.getReceiver() == null) {
            line = String.format("[%s] %s%n", msg.getSender(), msg.getContent());
        } else if (nick.equals(msg.getSender())) {
            line = String.format("[You -> %s] %s%n", msg.getReceiver(), msg.getContent());
        } else {
            line = String.format("[%s -> You] %s%n", msg.getSender(), msg.getContent());
        }
        messageArea.append(line);
        messageArea.setCaretPosition(messageArea.getDocument().getLength());
    }
    private void shutdown() {
        if (running.getAndSet(false)) {
            producer.close();
            publicConsumer.shutdown();
            privateConsumer.shutdown();
            dispose();
        }
    }
}