import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class reversetcpclient {
    public static void main(String[] args) {
        if (args.length != 5) {
            System.err.println("Usage: java reversetcpclient <serverIP> <serverPort> <filename> <Lmin> <Lmax>");
            System.exit(1);
        }

        String serverIP = args[0];
        int serverPort = Integer.parseInt(args[1]);
        String filename = args[2];
        int Lmin = Integer.parseInt(args[3]);
        int Lmax = Integer.parseInt(args[4]);

        try {
            // 读取文件内容
            String fileContent = new String(Files.readAllBytes(Paths.get(filename)), StandardCharsets.US_ASCII);

            // 分割文件成块
            List<String> blocks = splitFile(fileContent, Lmin, Lmax);
            int blockCount = blocks.size();

            try (Socket socket = new Socket(serverIP, serverPort);
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {

                // 发送初始化报文
                out.writeShort(1);
                out.writeInt(blockCount);
                out.flush();

                // 接收同意报文
                short type = in.readShort();
                if (type != 2) {
                    System.err.println("Invalid agreement packet");
                    return;
                }

                // 存储所有反转块
                List<String> reversedBlocks = new ArrayList<>();

                // 发送并接收每个数据块
                for (int i = 0; i < blockCount; i++) {
                    String block = blocks.get(i);

                    // 发送反转请求
                    out.writeShort(3);
                    out.writeInt(block.length());
                    out.writeBytes(block);
                    out.flush();

                    // 接收反转响应
                    type = in.readShort();
                    if (type != 4) {
                        System.err.println("Invalid answer packet");
                        break;
                    }

                    int length = in.readInt();
                    byte[] reversedData = new byte[length];
                    in.readFully(reversedData);

                    String reversedBlock = new String(reversedData, StandardCharsets.US_ASCII);
                    reversedBlocks.add(reversedBlock);
                    System.out.println((i+1) + ": " + reversedBlock);
                }

                // 生成最终反转文件
                String reversedContent = combineReversedBlocks(reversedBlocks);
                String outputFilename = filename + ".reversed";
                Files.write(Paths.get(outputFilename), reversedContent.getBytes(StandardCharsets.US_ASCII));
                System.out.println("Final reversed file saved as: " + outputFilename);

            } catch (IOException e) {
                System.err.println("Client error: " + e.getMessage());
            }
        } catch (IOException e) {
            System.err.println("File error: " + e.getMessage());
        }
    }

    private static List<String> splitFile(String content, int Lmin, int Lmax) {
        List<String> blocks = new ArrayList<>();
        Random random = new Random();
        int index = 0;
        int totalLength = content.length();

        while (index < totalLength) {
            int maxPossible = totalLength - index;
            int blockSize;

            if (maxPossible <= Lmax) {
                blockSize = maxPossible;
            } else {
                blockSize = Lmin + random.nextInt(Lmax - Lmin + 1);
            }

            blocks.add(content.substring(index, index + blockSize));
            index += blockSize;
        }

        return blocks;
    }

    private static String combineReversedBlocks(List<String> reversedBlocks) {
        StringBuilder result = new StringBuilder();
        for (int i = reversedBlocks.size() - 1; i >= 0; i--) {
            result.append(reversedBlocks.get(i));
        }
        return result.toString();
    }
}