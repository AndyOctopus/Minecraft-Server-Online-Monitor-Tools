package top.yzljc.utiltools;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainFrame extends JFrame {

    private final List<ServerInfo> serverList = Collections.synchronizedList(new ArrayList<>());
    private final DefaultTableModel tableModel;
    private final JTable table;
    private TrayIcon trayIcon;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public MainFrame() {
        setTitle("MC Server Monitor (Pro Edition)");
        setSize(800, 500);
        // 这里改成 DO_NOTHING，因为我们要自己接管关闭事件（最小化到托盘）
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);

        // 1. 初始化系统托盘 (带右键菜单)
        initSystemTray();

        // 2. 初始化窗口监听 (处理最小化/关闭逻辑)
        initWindowListeners();

        // 3. 初始化菜单栏 (开机自启)
        initMenuBar();

        // --- 加载数据 ---
        List<ServerInfo> savedData = DataManager.load();
        serverList.addAll(savedData);

        // --- UI 构建 ---
        var topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        var nameField = new JTextField("Example", 8);
        var ipField = new JTextField("127.0.0.1", 12);
        var portField = new JTextField("25565", 5);

        var addButton = new JButton("添加");
        var deleteButton = new JButton("删除选中");
        deleteButton.setForeground(Color.RED);

        topPanel.add(new JLabel("名称:"));
        topPanel.add(nameField);
        topPanel.add(new JLabel("IP:"));
        topPanel.add(ipField);
        topPanel.add(new JLabel("端口:"));
        topPanel.add(portField);
        topPanel.add(addButton);
        topPanel.add(deleteButton);

        String[] columnNames = {"名称", "IP地址", "端口", "状态", "最后检测时间"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(24);
        table.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(80);

        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        refreshTableUI();

        // --- 事件绑定 ---
        addButton.addActionListener(e -> {
            try {
                var name = nameField.getText().trim();
                var ip = ipField.getText().trim();
                var portStr = portField.getText().trim();

                if(name.isEmpty() || ip.isEmpty() || portStr.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "请填写完整信息");
                    return;
                }

                int port = Integer.parseInt(portStr);
                serverList.add(new ServerInfo(name, ip, port));
                DataManager.save(serverList);
                refreshTableUI();
                nameField.setText("");
                ipField.setText("");
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "端口必须是数字");
            }
        });

        deleteButton.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this, "请先选中要删除的行");
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(this, "确定删除吗？", "确认", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                serverList.remove(selectedRow);
                DataManager.save(serverList);
                refreshTableUI();
            }
        });

        // 启动检测任务
        scheduler.scheduleAtFixedRate(this::runChecks, 0, 10, TimeUnit.SECONDS);
    }

    // --- 新增功能区 ---

    /**
     * 初始化窗口监听器：拦截关闭按钮，改为最小化到托盘
     */
    private void initWindowListeners() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // 点击 X 时，隐藏窗口，不退出程序
                if (SystemTray.isSupported()) {
                    setVisible(false);
                    // 第一次隐藏时可以发个通知告诉用户去哪里找
                    // sendNotification("程序已隐藏", "MC监控正在后台运行，双击托盘图标恢复。");
                } else {
                    System.exit(0);
                }
            }

            @Override
            public void windowIconified(WindowEvent e) {
                // 如果你希望点击最小化按钮也隐藏任务栏图标，可以在这里 setVisible(false)
                // 这里保留默认行为（最小化到任务栏）
            }
        });
    }

    /**
     * 初始化菜单栏（设置 - 开机自启）
     */
    private void initMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu settingsMenu = new JMenu("设置");

        JCheckBoxMenuItem autoStartItem = new JCheckBoxMenuItem("开机自启动");

        // 检查当前是否已经是开机自启状态 (通过注册表检查比较复杂，这里简单处理：默认未选中，由用户操作)
        // 如果是在 IDE 中运行，获取不到实际 EXE 路径，禁用此功能
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath == null) {
            autoStartItem.setEnabled(false);
            autoStartItem.setToolTipText("请打包成 Exe 后使用此功能");
        }

        autoStartItem.addActionListener(e -> {
            toggleAutoStart(autoStartItem.isSelected());
        });

        settingsMenu.add(autoStartItem);
        menuBar.add(settingsMenu);
        setJMenuBar(menuBar);
    }

    /**
     * 开机自启逻辑 (操作 Windows 注册表)
     */
    private void toggleAutoStart(boolean enable) {
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath == null) return;

        String cmd;
        try {
            if (enable) {
                // 添加注册表: reg add HKCU\...\Run /v "AppName" /d "Path" /f
                cmd = String.format("reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run\" /v \"McMonitor\" /d \"%s\" /f", appPath);
            } else {
                // 删除注册表: reg delete HKCU\...\Run /v "AppName" /f
                cmd = "reg delete \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run\" /v \"McMonitor\" /f";
            }

            // 执行 CMD 命令
            Runtime.getRuntime().exec(cmd);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "设置开机自启失败: " + e.getMessage());
        }
    }

    /**
     * 初始化托盘图标及右键菜单
     */
    private void initSystemTray() {
        if (!SystemTray.isSupported()) return;
        try {
            var tray = SystemTray.getSystemTray();
            // 绘制一个简单的图标 (如果有 icon.png 请替换 ImageIO.read(...))
            var image = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var g = image.createGraphics();
            g.setColor(new Color(60, 179, 113)); // MC Green
            g.fillRect(0, 0, 16, 16);
            g.dispose();

            // 创建右键弹出菜单
            PopupMenu popup = new PopupMenu();
            MenuItem showItem = new MenuItem("Show Monitor");
            MenuItem exitItem = new MenuItem("Exit");

            showItem.addActionListener(e -> {
                setVisible(true);
                setExtendedState(JFrame.NORMAL);
                toFront();
            });

            exitItem.addActionListener(e -> {
                System.exit(0);
            });

            popup.add(showItem);
            popup.addSeparator();
            popup.add(exitItem);

            trayIcon = new TrayIcon(image, "MC Monitor", popup);
            trayIcon.setImageAutoSize(true);

            // 双击托盘图标打开窗口
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        setVisible(true);
                        setExtendedState(JFrame.NORMAL);
                        toFront();
                    }
                }
            });

            tray.add(trayIcon);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- 核心逻辑区 (包含修改后的离线提醒) ---

    private void runChecks() {
        if (serverList.isEmpty()) return;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var server : serverList) {
                executor.submit(() -> checkServer(server));
            }
        }
        SwingUtilities.invokeLater(this::refreshTableUI);
    }

    private void checkServer(ServerInfo server) {
        boolean isOnlineNow;
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(server.getIp(), server.getPort()), 3000);
            isOnlineNow = true;
        } catch (Exception e) {
            isOnlineNow = false;
        }

        boolean wasOnline = server.isOnline();
        boolean isFirst = server.isFirstCheck();

        // 状态发生改变
        if (isOnlineNow != wasOnline) {
            if (!isFirst) {
                if (isOnlineNow) {
                    // 上线通知 (蓝色 INFO)
                    sendNotification("服务器上线啦！",
                            "[" + server.getName() + "] 终于上线了，快去连接吧！", TrayIcon.MessageType.INFO);
                } else {
                    // 掉线通知 (黄色 WARNING)
                    sendNotification("服务器掉线了...",
                            "[" + server.getName() + "] 刚刚断开了连接。", TrayIcon.MessageType.WARNING);
                }
            }
        }

        server.setOnline(isOnlineNow);
        server.setFirstCheck(false);
    }

    private void sendNotification(String title, String content, TrayIcon.MessageType type) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, content, type);
        }
    }

    private void refreshTableUI() {
        int selectedRow = table.getSelectedRow();
        tableModel.setRowCount(0);
        synchronized (serverList) {
            var timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            for (var s : serverList) {
                tableModel.addRow(new Object[]{
                        s.getName(),
                        s.getIp(),
                        s.getPort(),
                        s.isOnline() ? "🟢 在线" : "🔴 离线",
                        s.isFirstCheck() ? "等待检测..." : timeStr
                });
            }
        }
        if (selectedRow >= 0 && selectedRow < table.getRowCount()) {
            table.setRowSelectionInterval(selectedRow, selectedRow);
        }
    }
}