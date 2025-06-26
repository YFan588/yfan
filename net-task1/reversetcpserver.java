import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class reversetcpserver {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java reversetcpserver <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        ExecutorService threadPool = Executors.newCachedThreadPool();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Server exception: " + e.getMessage());
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                 DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

                // 读取初始化报文
                short type = in.readShort();
                if (type != 1) {
                    System.err.println("Invalid initialization packet");
                    return;
                }
                int blockCount = in.readInt();

                // 发送同意报文
                out.writeShort(2);
                out.flush();

                // 处理每个数据块
                for (int i = 0; i < blockCount; i++) {
                    type = in.readShort();
                    if (type != 3) {
                        System.err.println("Invalid request packet");
                        break;
                    }

                    int length = in.readInt();
                    byte[] data = new byte[length];
                    in.readFully(data);

                    // 反转数据
                    String original = new String(data, StandardCharsets.US_ASCII);
                    String reversed = new StringBuilder(original).reverse().toString();

                    // 发送响应报文
                    out.writeShort(4);
                    out.writeInt(reversed.length());
                    out.writeBytes(reversed);
                    out.flush();
                }
            } catch (IOException e) {
                System.err.println("Client handling exception: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Socket close error: " + e.getMessage());
                }
            }
        }
    }
}