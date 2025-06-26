import java.nio.ByteBuffer;

public class Packet {
    // 报文类型
    public static final byte SYN = 0x1;
    public static final byte SYN_ACK = 0x2;
    public static final byte ACK = 0x4;
    public static final byte DATA = 0x8;
    public static final byte FIN = 0x10;

    // 头部长度 (1+4+4+8 = 17字节)
    private byte type;
    private int seqNumber;    // 序列号（字节偏移）
    private int ackNumber;    // 确认号（字节偏移）
    private long timestamp;   // 发送时间戳
    private byte[] payload;   // 数据负载

    public Packet(byte type, int seqNumber, int ackNumber, byte[] payload) {
        this.type = type;
        this.seqNumber = seqNumber;
        this.ackNumber = ackNumber;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }

    // 序列化
    public byte[] serialize() {
        int payloadLength = (payload != null) ? payload.length : 0;
        ByteBuffer buffer = ByteBuffer.allocate(17 + payloadLength);
        buffer.put(type);
        buffer.putInt(seqNumber);
        buffer.putInt(ackNumber);
        buffer.putLong(timestamp);
        if (payload != null) {
            buffer.put(payload);
        }
        return buffer.array();
    }

    // 反序列化
    public static Packet deserialize(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        byte type = buffer.get();
        int seq = buffer.getInt();
        int ack = buffer.getInt();
        long timestamp = buffer.getLong();

        byte[] payload = null;
        if (buffer.remaining() > 0) {
            payload = new byte[buffer.remaining()];
            buffer.get(payload);
        }

        return new Packet(type, seq, ack, payload);
    }

    // Getters
    public byte getType() { return type; }
    public int getSeqNumber() { return seqNumber; }
    public int getAckNumber() { return ackNumber; }
    public long getTimestamp() { return timestamp; }
    public byte[] getPayload() { return payload; }
    public int getLength() { return (payload != null) ? payload.length : 0; }
}