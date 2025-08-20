package com.example.kafkachat;

import com.example.kafkachat.gui.ChatGui;
import com.example.kafkachat.gui.StartingUpDialog;

import javax.swing.*;

/**
 * Точка входа Swing-клиента Kafka-чата.
 *
 * Алгоритм работы:
 * 1. Настраиваем Look&Feel (предпочитаем Nimbus, если доступен).
 * 2. Показываем модальный диалог ‑ {@link StartingUpDialog}.
 * 3. Если пользователь нажал «Start», открываем главное окно чата {@link ChatGui}.
 */
public class Main {
    public static void main(String[] args) {
        // Попытка применить более симпатичный Look&Feel
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Throwable ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            // Диалог для ввода ника и комнаты
            StartingUpDialog dlg = new StartingUpDialog(null);
            dlg.setVisible(true);

            if (!dlg.isConfirmed()) {
                System.exit(0);
            }

            // Открываем главное окно чата
            new ChatGui(dlg.getNickname(), dlg.getRoom());
        });
    }
}