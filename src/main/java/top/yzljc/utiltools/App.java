package top.yzljc.utiltools;

import javax.swing.*;

public class App {
    public static void main(String[] args) {
        try {
            // 启用 Windows 系统风格
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}