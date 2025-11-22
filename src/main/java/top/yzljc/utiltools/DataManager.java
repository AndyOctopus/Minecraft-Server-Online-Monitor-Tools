package top.yzljc.utiltools;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DataManager {
    // 数据保存在运行目录下的 servers.txt 文件中
    private static final String FILE_PATH = "servers.txt";
    private static final String SPLIT_TAG = "###";

    /**
     * 保存列表到文件
     */
    public static void save(List<ServerInfo> servers) {
        // 使用 try-with-resources 自动关闭流
        try (PrintWriter writer = new PrintWriter(new FileWriter(FILE_PATH, StandardCharsets.UTF_8))) {
            for (ServerInfo server : servers) {
                // 格式: 名称###IP###端口
                // 防止名称中包含分隔符，简单替换一下
                String safeName = server.getName().replace(SPLIT_TAG, "_");
                String line = safeName + SPLIT_TAG + server.getIp() + SPLIT_TAG + server.getPort();
                writer.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            // 实际生产中可以弹窗提示保存失败，这里简单打印堆栈
        }
    }

    /**
     * 从文件加载列表
     */
    public static List<ServerInfo> load() {
        List<ServerInfo> list = new ArrayList<>();
        File file = new File(FILE_PATH);

        // 如果文件不存在（第一次运行），直接返回空列表
        if (!file.exists()) {
            return list;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                String[] parts = line.split(SPLIT_TAG);
                // 必须满足3个部分：Name, IP, Port
                if (parts.length == 3) {
                    try {
                        String name = parts[0];
                        String ip = parts[1];
                        int port = Integer.parseInt(parts[2]);

                        // 加载进来的默认都是离线状态，等待第一次检测
                        list.add(new ServerInfo(name, ip, port));
                    } catch (NumberFormatException ignored) {
                        // 忽略格式错误的行
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list;
    }
}