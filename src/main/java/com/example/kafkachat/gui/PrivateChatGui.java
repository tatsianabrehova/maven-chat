package com.example.kafkachat.gui;

import com.example.kafkachat.kafka.KafkaProducerService;
import com.example.kafkachat.model.ChatMessage;
import com.example.kafkachat.service.UserRoster;

import javax.swing.*;
import java.awt.*;
import java.util.List;
/**
 * КЛАСС PrivateChatGui - ОКНО ПРИВАТНОГО ЧАТА
 *
 * ЧТО ЭТОТ КЛАСС ДЕЛАЕТ:
 * 1. Создает отдельное окно для приватной переписки между двумя пользователями
 * 2. Отображает историю сообщений из UserRoster при открытии чата
 * 3. Отправляет новые сообщения через KafkaProducerService в топик "private-messages"
 * 4. Сохраняет сообщения в UserRoster для последующего доступа к истории
 * 5. Автоматически обновляет интерфейс при получении новых сообщений
 * 6. Обеспечивает удобный интерфейс для ввода и отправки сообщений
 *
 * ОСОБЕННОСТИ РЕАЛИЗАЦИИ:
 * - Использует SwingUtilities.invokeLater для потокобезопасного обновления UI
 * - Автоматически прокручивает текст к последнему сообщению
 * - Различает "свои" и "чужие" сообщения в отображении
 * - Поддерживает отправку сообщений по кнопке и клавише Enter
 */
public class PrivateChatGui extends JFrame {
    private final KafkaProducerService producer;
    private final String sender, receiver;
    private final UserRoster userRoster;

    private final JTextArea  area   = new JTextArea(15, 40);
    private final JTextField input  = new JTextField(30);
    private final JButton    send   = new JButton("Send");

    public PrivateChatGui(KafkaProducerService producer,
                          String sender,
                          String receiver,
                          UserRoster userRoster) {
        this.producer   = producer;
        this.sender     = sender;
        this.receiver   = receiver;
        this.userRoster = userRoster;

        setTitle("Private: " + sender + " ↔ " + receiver);
        setSize(500, 400);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        area.setEditable(false);
        add(new JScrollPane(area), BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.add(input, BorderLayout.CENTER);
        south.add(send, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);
        send.addActionListener(e -> sendMsg());
        input.addActionListener(e -> sendMsg());
        loadHistory();
        setVisible(true);
    }

    private void loadHistory() {
        List<ChatMessage> hist = userRoster.getDialogHistory(sender, receiver);
        SwingUtilities.invokeLater(() -> hist.forEach(this::printMsg));
    }

    private void sendMsg() {
        String text = input.getText().trim();
        if (text.isEmpty()) return;

        ChatMessage msg = new ChatMessage(sender, text, null, receiver);
        producer.sendMessage("private-messages", msg);
        userRoster.sendPrivateMessage(sender, receiver, text);
        printMsg(msg);
        input.setText("");
    }

    private void printMsg(ChatMessage m) {
        String nick = m.getSender().equals(sender) ? "You" : m.getSender();
        area.append(String.format("[%s] %s: %s\n", m.getTimestamp(), nick, m.getContent()));
        area.setCaretPosition(area.getDocument().getLength());
    }
}