import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.Random; // 添加Random导入

public class ReliableUDPClient {
    // 配置参数
    private static final int WINDOW_SIZE = 400;  // 窗口大小（字节）
    private static final int MIN_PACKET_SIZE = 40; // 最小包大小
    private static final int MAX_PACKET_SIZE = 80; // 最大包大小
    private static final int TOTAL_DATA = 2400;   // 总数据量（30个包）
    private static final int INIT_TIMEOUT = 300;  // 初始超时时间(ms)
    private static final int MAX_RETRIES = 5;     // 最大重传次数

    // 网络组件
    private DatagramSocket socket;
    private InetAddress serverAddr;
    private int serverPort;
    private Random random = new Random(); // 添加Random成员变量

    // 传输状态
    private int base = 0;          // 窗口起始
    private int nextSeq = 0;        // 下一个发送位置
    private int packetCounter = 1;  // 包计数器

    // 包管理
    private final Map<Integer, Packet> sentPackets = new ConcurrentHashMap<>();
    private final Map<Integer, Long> sendTimes = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> retryCounts = new ConcurrentHashMap<>();
    private final Map<Integer, int[]> packetRanges = new ConcurrentHashMap<>();

    // 统计
    private final List<Long> rttList = new ArrayList<>();
    private int totalSent = 0;
    private int totalResent = 0;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public ReliableUDPClient(String host, int port) throws Exception {
        this.serverAddr = InetAddress.getByName(host);
        this.serverPort = port;
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout(INIT_TIMEOUT);
        this.random = new Random(); // 初始化Random
    }

    public void start() throws Exception {
        // 建立连接
        establishConnection();

        // 启动ACK接收线程
        Thread receiver = new Thread(this::receiveAcks);
        receiver.setDaemon(true);
        receiver.start();

        // 启动超时检测
        scheduler.scheduleAtFixedRate(this::checkTimeouts, 50, 50, TimeUnit.MILLISECONDS);

        // 发送数据
        while (base < TOTAL_DATA) {
            // 填充发送窗口
            while (nextSeq < base + WINDOW_SIZE && nextSeq < TOTAL_DATA) {
                sendNextPacket();
            }

            // 避免CPU忙等待
            Thread.sleep(10);
        }

        // 关闭连接
        closeConnection();

        // 清理资源
        scheduler.shutdown();
        scheduler.awaitTermination(1, TimeUnit.SECONDS);

        // 打印统计
        printStatistics();
    }

    private void sendNextPacket() throws IOException {
        // 确定包大小
        int packetSize = Math.min(
                MIN_PACKET_SIZE + random.nextInt(MAX_PACKET_SIZE - MIN_PACKET_SIZE + 1),
                TOTAL_DATA - nextSeq
        );

        // 创建数据包
        byte[] payload = new byte[packetSize];
        Arrays.fill(payload, (byte) 1); // 填充测试数据

        Packet packet = new Packet(Packet.DATA, nextSeq, 0, payload);
        sendPacket(packet);

        // 记录包信息
        int start = nextSeq;
        int end = nextSeq + packetSize - 1;
        packetRanges.put(start, new int[]{start, end});
        sendTimes.put(start, System.currentTimeMillis());
        sentPackets.put(start, packet);
        retryCounts.put(start, 0);

        // 输出发送信息
        System.out.printf("第 %d 个（第 %d~%d 字节）client 端已经发送%n",
                packetCounter, start, end);

        // 更新状态
        nextSeq += packetSize;
        packetCounter++;
        totalSent++;
    }

    private void establishConnection() throws IOException {
        // 发送SYN
        Packet syn = new Packet(Packet.SYN, 0, 0, null);
        sendPacket(syn);

        // 等待SYN-ACK
        byte[] buffer = new byte[1024];
        DatagramPacket response = new DatagramPacket(buffer, buffer.length);
        socket.receive(response);

        Packet synAck = Packet.deserialize(Arrays.copyOf(response.getData(), response.getLength()));
        if ((synAck.getType() & Packet.SYN_ACK) == 0) {
            throw new IOException("连接失败");
        }

        System.out.println("连接已建立");
    }

    private void closeConnection() throws IOException {
        // 发送FIN
        Packet fin = new Packet(Packet.FIN, 0, 0, null);
        sendPacket(fin);

        // 等待FIN-ACK
        byte[] buffer = new byte[1024];
        DatagramPacket response = new DatagramPacket(buffer, buffer.length);
        socket.setSoTimeout(2000);
        try {
            socket.receive(response);
            Packet finAck = Packet.deserialize(Arrays.copyOf(response.getData(), response.getLength()));
            if ((finAck.getType() & Packet.ACK) == 0) {
                throw new IOException("关闭失败");
            }
            System.out.println("连接已关闭");
        } catch (SocketTimeoutException e) {
            System.err.println("关闭超时，强制关闭");
        }
    }

    private void sendPacket(Packet packet) throws IOException {
        byte[] data = packet.serialize();
        DatagramPacket datagram = new DatagramPacket(data, data.length, serverAddr, serverPort);
        socket.send(datagram);
    }

    private void receiveAcks() {
        try {
            byte[] buffer = new byte[1024];
            while (!Thread.currentThread().isInterrupted()) {
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                socket.receive(response);

                Packet packet = Packet.deserialize(Arrays.copyOf(response.getData(), response.getLength()));
                if ((packet.getType() & Packet.ACK) != 0) {
                    handleAck(packet.getAckNumber());
                }
            }
        } catch (SocketException e) {
            // 正常退出
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleAck(int ackNum) {
        if (ackNum > base) {
            // 处理所有被确认的包
            Iterator<Integer> it = new ArrayList<>(sendTimes.keySet()).iterator();
            while (it.hasNext()) {
                int seq = it.next();
                if (seq < ackNum) {
                    // 计算RTT
                    long rtt = System.currentTimeMillis() - sendTimes.get(seq);
                    rttList.add(rtt);

                    // 输出确认信息
                    int[] range = packetRanges.get(seq);
                    System.out.printf("第 %d 个（第 %d~%d 字节）server 端已经收到，RTT 是 %d ms%n",
                            getPacketNumber(seq), range[0], range[1], rtt);

                    // 移除已确认包
                    sendTimes.remove(seq);
                    sentPackets.remove(seq);
                    packetRanges.remove(seq);
                    retryCounts.remove(seq);
                }
            }

            // 推进窗口
            base = ackNum;
        }
    }

    private int getPacketNumber(int seq) {
        // 简化实现：返回包在发送顺序中的编号
        for (Map.Entry<Integer, int[]> entry : packetRanges.entrySet()) {
            if (entry.getKey() == seq) {
                // 在实际应用中应维护映射关系，这里简化处理
                return packetRanges.keySet().stream()
                        .filter(k -> k <= seq)
                        .mapToInt(k -> 1)
                        .sum();
            }
        }
        return -1;
    }

    private void checkTimeouts() {
        long now = System.currentTimeMillis();
        int timeout = calculateTimeout();

        // 检查所有未确认包
        for (Map.Entry<Integer, Long> entry : new HashMap<>(sendTimes).entrySet()) {
            int seq = entry.getKey();
            long sendTime = entry.getValue();

            if (now - sendTime > timeout) {
                int retries = retryCounts.getOrDefault(seq, 0);

                if (retries >= MAX_RETRIES) {
                    System.err.printf("达到最大重传次数，放弃包: %d (字节 %d-%d)%n",
                            getPacketNumber(seq),
                            packetRanges.get(seq)[0],
                            packetRanges.get(seq)[1]);
                    sendTimes.remove(seq);
                    continue;
                }

                try {
                    // 重发包
                    Packet packet = sentPackets.get(seq);
                    sendPacket(packet);

                    // 更新状态
                    sendTimes.put(seq, now);
                    retryCounts.put(seq, retries + 1);
                    totalResent++;

                    // 输出重传信息
                    int[] range = packetRanges.get(seq);
                    System.out.printf("重传第 %d 个（第 %d~%d 字节）数据包%n",
                            getPacketNumber(seq), range[0], range[1]);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private int calculateTimeout() {
        if (rttList.isEmpty()) return INIT_TIMEOUT;

        // 使用平均RTT的5倍
        long sum = 0;
        for (long rtt : rttList) sum += rtt;
        return (int) (sum / rttList.size() * 5);
    }

    private void printStatistics() {
        int totalPackets = totalSent + totalResent;
        double lossRate = (double) totalResent / totalPackets * 100;

        System.out.println("\n===== 传输统计 =====");
        System.out.printf("总发送包数: %d%n", totalPackets);
        System.out.printf("原始发送包数: %d%n", totalSent);
        System.out.printf("重传包数: %d%n", totalResent);
        System.out.printf("丢包率: %.2f%%%n", lossRate);

        if (!rttList.isEmpty()) {
            long minRtt = Collections.min(rttList);
            long maxRtt = Collections.max(rttList);
            double avgRtt = RTTStatistics.calculateMean(rttList);
            double stdDev = RTTStatistics.calculateStdDev(rttList);

            System.out.println("RTT统计:");
            System.out.printf("  最小值: %d ms%n", minRtt);
            System.out.printf("  最大值: %d ms%n", maxRtt);
            System.out.printf("  平均值: %.2f ms%n", avgRtt);
            System.out.printf("  标准差: %.2f ms%n", stdDev);
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("用法: java ReliableUDPClient <主机> <端口>");
            return;
        }

        try {
            String host = args[0];
            int port = Integer.parseInt(args[1]);
            ReliableUDPClient client = new ReliableUDPClient(host, port);
            client.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}