package com.example.kafkachat.gui;

import com.example.kafkachat.kafka.KafkaConsumerService;
import com.example.kafkachat.kafka.KafkaProducerService;
import com.example.kafkachat.model.ChatMessage;
import com.example.kafkachat.service.UserRoster;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Основное окно чата (ChatGui).
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
    private final JList<String> userList   = new JList<>(userListModel);
    private final String room;
    private final Timer wakeupTimer;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final String initialNick;

    public ChatGui(String nickname, String room) {
        this.producer = new KafkaProducerService();
        this.room     = room;
        this.initialNick = nickname;
        this.userRoster = UserRoster.getInstance();

        setTitle("Kafka Chat – Room: " + room);
        setSize(800, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) { shutdown(); }
        });

        nicknameField.setText(nickname);
        messageArea.setEditable(false);
        messageArea.setFont(new Font("Dialog", Font.PLAIN, 14));
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        buildLayout();
        wireActions();

        publicConsumer  = new KafkaConsumerService("chat-room-" + room,
                this::handlePublicMessage);
        privateConsumer = new KafkaConsumerService("private-messages",
                this::handlePrivateMessage);

        loadChatHistory();
        refreshUserList();
        userRoster.joinRoom(room, nickname);
        wakeupTimer = new Timer(500, e -> {
            publicConsumer.wakeup();
            privateConsumer.wakeup();
        });
        wakeupTimer.start();
        setVisible(true);
    }

    private void buildLayout() {
        setLayout(new BorderLayout(5, 5));

        JPanel top = new JPanel(new FlowLayout());
        top.add(new JLabel("Nickname:"));
        top.add(nicknameField);

        JButton exitBtn = new JButton("Покинуть комнату");
        exitBtn.addActionListener(e -> exitToRoomSelector());
        top.add(exitBtn);

        JPanel right = new JPanel(new BorderLayout());
        right.add(new JLabel("Users"), BorderLayout.NORTH);
        right.add(new JScrollPane(userList), BorderLayout.CENTER);
        right.setPreferredSize(new Dimension(150, 200));

        JButton privBtn = new JButton("Private Chat");
        right.add(privBtn, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(messageArea), BorderLayout.CENTER);
        add(right, BorderLayout.EAST);

        JPanel bottom = new JPanel(new FlowLayout());
        bottom.add(inputField);
        bottom.add(sendButton);
        add(bottom, BorderLayout.SOUTH);
    }

    private void wireActions() {
        ActionListener sendAction = e -> {
            String nick = nicknameField.getText().trim();
            String text = inputField.getText().trim();
            if (!nick.isEmpty() && !text.isEmpty()) {
                ChatMessage msg = new ChatMessage(nick, text, room, null);
                producer.sendMessage("chat-room-" + room, msg);
                // Immediate feedback
                userRoster.sendRoomMessage(room, msg);
                SwingUtilities.invokeLater(() -> displayMessage(msg));
                inputField.setText("");
            }
        };
        sendButton.addActionListener(sendAction);
        inputField.addActionListener(sendAction);

        JButton privChatBtn = ((JButton) ((JPanel) getContentPane()
                .getComponent(2)).getComponent(2));
        privChatBtn.addActionListener(e -> openPrivateChat());
        nicknameField.addActionListener(e -> refreshUserList());
    }

    private void loadChatHistory() {
        SwingUtilities.invokeLater(() -> {
            messageArea.setText("");
            userRoster.getRoomHistory(room)
                    .forEach(this::displayMessage);
        });
    }

    private void refreshUserList() {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            userRoster.allUsers().forEach(userListModel::addElement);
        });
    }

    private void openPrivateChat() {
        String receiver = userList.getSelectedValue();
        String sender   = nicknameField.getText().trim();
        if (receiver == null) {
            JOptionPane.showMessageDialog(this, "Выберите пользователя!");
            return;
        }
        new PrivateChatGui(producer, sender, receiver, userRoster);
    }

    private void handlePublicMessage(ChatMessage msg) {
        // ★★★ ДОБАВЬТЕ ЛОГИРОВАНИЕ ★★★
        System.out.println("HANDLE PUBLIC: " + msg.getSender() + ": " + msg.getContent() + " Room: " + msg.getRoom());

        if ("System".equals(msg.getSender())) return;

        // ★★★ ПРОВЕРЯЕМ ДЛЯ КАКОЙ КОМНАТЫ СООБЩЕНИЕ ★★★
        if (room.equals(msg.getRoom())) {
            System.out.println("Message for our room! Displaying...");
            userRoster.sendRoomMessage(room, msg);
            SwingUtilities.invokeLater(() -> displayMessage(msg));
        } else {
            System.out.println("Message for different room: " + msg.getRoom() + ", our room: " + room);
        }
    }

    private void handlePrivateMessage(ChatMessage msg) {
        String myNick = nicknameField.getText().trim();
        // Принимаем сообщения, где мы получатель ИЛИ отправитель
        if (myNick.equals(msg.getReceiver()) || myNick.equals(msg.getSender())) {
            // Сохраняем в историю только если это новое входящее сообщение
            if (myNick.equals(msg.getReceiver())) {
                userRoster.sendPrivateMessage(msg.getSender(), myNick, msg.getContent());
            }
            SwingUtilities.invokeLater(() -> displayMessage(msg));
        }
    }

    private void displayMessage(ChatMessage msg) {
        String text;
        if (msg.getReceiver() == null) {
            text = String.format("[%s] %s", msg.getSender(), msg.getContent());
        } else {
            String nick = nicknameField.getText().trim();
            if (nick.equals(msg.getSender())) {
                text = String.format("[You -> %s] %s", msg.getReceiver(), msg.getContent());
            } else {
                text = String.format("[%s -> You] %s", msg.getSender(), msg.getContent());
            }
        }
        messageArea.append(text + "\n");
        messageArea.setCaretPosition(messageArea.getDocument().getLength());
    }

    private void shutdown() {
        if (running.compareAndSet(true, false)) {
            producer.close();
            publicConsumer.shutdown();
            privateConsumer.shutdown();
            dispose();
        }
    }

    private void exitToRoomSelector() {
        shutdown();
        dispose();
        SwingUtilities.invokeLater(() -> {
            StartingUpDialog dlg = new StartingUpDialog(null, initialNick);
            dlg.setVisible(true);
            if (dlg.isConfirmed()) {
                new ChatGui(dlg.getNickname(), dlg.getRoom());
            }
        });
    }
    @Override
    public void dispose() {
        if (wakeupTimer != null) {
            wakeupTimer.stop();
        }
        super.dispose();
    }
}