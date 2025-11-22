package top.yzljc.utiltools;

public class ServerInfo {
    private final String name;
    private final String ip;
    private final int port;

    // 状态由 volatile 修饰以保证多线程可见性
    private volatile boolean isOnline = false;
    private volatile boolean isFirstCheck = true;

    public ServerInfo(String name, String ip, int port) {
        this.name = name;
        this.ip = ip;
        this.port = port;
    }

    public String getName() { return name; }
    public String getIp() { return ip; }
    public int getPort() { return port; }

    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { this.isOnline = online; }

    public boolean isFirstCheck() { return isFirstCheck; }
    public void setFirstCheck(boolean firstCheck) { this.isFirstCheck = firstCheck; }
}