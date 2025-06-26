import java.net.*;
import java.io.*;
import java.util.*;

public class ReliableUDPServer {
    private static final int BUFFER_SIZE = 1024;
    private static final double LOSS_RATE = 0.2; // 20%丢包率

    private DatagramSocket socket;
    private int port;
    private Random random = new Random();

    // 接收状态
    private int expectedSeq = 0; // 下一个期望的字节序号
    private Map<Integer, byte[]> outOfOrder = new TreeMap<>(); // 乱序包缓存
    private Set<Integer> processedPackets = new HashSet<>(); // 已处理包记录

    public ReliableUDPServer(int port) throws SocketException {
        this.port = port;
        this.socket = new DatagramSocket(port);
        System.out.println("服务器启动，端口: " + port);
    }

    public void start() throws IOException {
        while (true) {
            byte[] buffer = new byte[BUFFER_SIZE];
            DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
            socket.receive(datagram);

            Packet packet = Packet.deserialize(
                    Arrays.copyOf(datagram.getData(), datagram.getLength())
            );
            InetAddress clientAddr = datagram.getAddress();
            int clientPort = datagram.getPort();

            // 处理连接请求
            if ((packet.getType() & Packet.SYN) != 0) {
                handleSYN(packet, clientAddr, clientPort);
            }
            // 处理数据包
            else if ((packet.getType() & Packet.DATA) != 0) {
                handleData(packet, clientAddr, clientPort);
            }
            // 处理结束请求
            else if ((packet.getType() & Packet.FIN) != 0) {
                handleFIN(clientAddr, clientPort);
                break;
            }
        }
        socket.close();
    }

    private void handleSYN(Packet packet, InetAddress clientAddr, int clientPort)
            throws IOException {
        System.out.println("收到来自 " + clientAddr + " 的连接请求");
        Packet synAck = new Packet(Packet.SYN_ACK, 0, packet.getSeqNumber() + 1, null);
        sendPacket(synAck, clientAddr, clientPort);
    }

    private void handleData(Packet packet, InetAddress clientAddr, int clientPort)
            throws IOException {
        int seq = packet.getSeqNumber();
        int length = packet.getLength();

        // 检查是否已处理过
        if (seq < expectedSeq) {
            // 重复包，直接忽略
            return;
        }

        // 检查是否为重传包
        boolean isRetransmission = processedPackets.contains(seq);

        // 对新包应用丢包率
        if (!isRetransmission && random.nextDouble() < LOSS_RATE) {
            System.out.printf("模拟丢包: 字节 %d~%d%n", seq, seq + length - 1);
            return;
        }

        // 标记为已处理
        processedPackets.add(seq);

        // 按序接收处理
        if (seq == expectedSeq) {
            // 处理当前包
            expectedSeq += length;

            // 处理后续的缓存包
            processBufferedPackets();
        } else {
            // 缓存乱序包
            outOfOrder.put(seq, packet.getPayload());
        }

        // 发送累积确认
        Packet ack = new Packet(Packet.ACK, 0, expectedSeq, null);
        sendPacket(ack, clientAddr, clientPort);
        System.out.printf("确认字节: %d (累积确认)%n", expectedSeq);
    }

    private void processBufferedPackets() {
        // 检查缓存中是否有连续的包
        while (outOfOrder.containsKey(expectedSeq)) {
            byte[] data = outOfOrder.remove(expectedSeq);
            expectedSeq += data.length;
        }
    }

    private void handleFIN(InetAddress clientAddr, int clientPort) throws IOException {
        System.out.println("收到结束请求，关闭连接");
        Packet finAck = new Packet((byte)(Packet.ACK | Packet.FIN), 0, expectedSeq, null);
        sendPacket(finAck, clientAddr, clientPort);
    }

    private void sendPacket(Packet packet, InetAddress addr, int port) throws IOException {
        byte[] data = packet.serialize();
        DatagramPacket datagram = new DatagramPacket(data, data.length, addr, port);
        socket.send(datagram);
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("用法: java ReliableUDPServer <端口>");
            return;
        }

        try {
            int port = Integer.parseInt(args[0]);
            ReliableUDPServer server = new ReliableUDPServer(port);
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}