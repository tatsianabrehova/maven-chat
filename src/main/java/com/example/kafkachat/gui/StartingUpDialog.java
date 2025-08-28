package com.example.kafkachat.gui;

import javax.swing.*;
import java.awt.*;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.example.kafkachat.service.UserRoster;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsResult;
/**
 * Диалоговое окно для начальной настройки чата
 *
 * ЧТО МЫ ДЕЛАЕМ В ЭТОМ КЛАССЕ:
 * 1. Создаем модальное диалоговое окно для ввода никнейма и выбора комнаты
 * 2. Асинхронно подключаемся к Kafka и получаем список доступных топиков-комнат
 * 3. Предоставляем интерфейс для выбора существующей комнаты или создания новой
 * 4. Валидируем введенные пользователем данные
 * 5. Возвращаем выбранные значения в основное приложение
 * 6. Обрабатываем ошибки подключения к Kafka
 */
public class StartingUpDialog extends JDialog {
    private final DefaultListModel<String> topicsModel = new DefaultListModel<>();
    private final JList<String> topicList  = new JList<>(topicsModel);
    private final JTextField  roomField    = new JTextField(15);
    private final JTextField  nicknameField = new JTextField(15);
    private final JButton okBtn = new JButton("OK");

    private String nickname;
    private String room;

    public StartingUpDialog(Frame parent) {
        super(parent, "Join Chat", true);
        setSize(400, 350);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(5, 5));
        add(buildPanel(), BorderLayout.CENTER);

        okBtn.addActionListener(e -> okPressed());
        getRootPane().setDefaultButton(okBtn);

        fetchTopics();                       // асинхронно
    }
    public StartingUpDialog(Frame parent, String initialNick) {
        this(parent);
        nicknameField.setText(initialNick);   // имя уже введено
        nicknameField.setEditable(false);     // пользователь может только комнату изменить
    }
    private JPanel buildPanel() {
        JPanel p = new JPanel(new GridLayout(4, 1, 5, 5));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ник
        p.add(labelled("Nickname:", nicknameField));

        // комната
        JLabel labRoom = new JLabel("Room:");
        JPanel topRoom = new JPanel(new BorderLayout());
        topRoom.add(labRoom, BorderLayout.WEST);
        topRoom.add(roomField, BorderLayout.CENTER);
        p.add(topRoom);

        // список топиков
        topicList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        topicList.addListSelectionListener(e -> {
            String selected = topicList.getSelectedValue();
            if (selected != null) roomField.setText(selected);
        });
        // двойной клик = OK
        topicList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) okPressed();
            }
        });

        p.add(new JLabel("Existing topics (click or double-click):"));
        p.add(new JScrollPane(topicList));

        JPanel bottom = new JPanel();
        bottom.add(okBtn);
        p.add(bottom);
        return p;
    }

    private static JPanel labelled(String text, JTextField f) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JLabel(text), BorderLayout.WEST);
        p.add(f, BorderLayout.CENTER);
        return p;
    }

    private void fetchTopics() {
        new SwingWorker<Set<String>, Void>() {
            protected Set<String> doInBackground() throws Exception {
                Properties props = new Properties();
                props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
                try (AdminClient client = AdminClient.create(props)) {
                    ListTopicsResult result    = client.listTopics();
                    return result.names().get()
                            .stream()
                            .filter(name -> name.startsWith("chat-room-"))
                            .map(name -> name.substring("chat-room-".length()))
                            .collect(Collectors.toCollection(TreeSet::new));
                }
            }
            protected void done() {
                try {
                    topicsModel.clear();
                    get().forEach(topicsModel::addElement);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                            StartingUpDialog.this,
                            "Не удалось получить список топиков:\n" + ex.getMessage(),
                            "Ошибка",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void okPressed() {
        nickname = nicknameField.getText().trim();
        room     = roomField.getText().trim();
        if (nickname.isEmpty() || room.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Укажите ник и имя комнаты",
                    "Ошибка",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        UserRoster.getInstance().register(nickname);
        dispose();   // закрыли
    }

    // геттеры для вызывающего кода
    public String getNickname() { return nickname; }
    public String getRoom()      { return room; }
    public boolean isConfirmed() { return nickname != null && room != null; }
}