package bgu.spl.net.impl.tftp;

import java.util.Arrays;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {

    private byte[] bytes = new byte[1 << 10]; // start with 1k
    private int len = 0; // the current number of bytes we got
    private Short opcode = null;
    private Short dataSize = 0;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        pushByte(nextByte);
        if (len == 2) {
            opcode = (short) (((short) bytes[0]) << 8 | (short) (bytes[1]));
        }
        if (opcode != null
                && (opcode == 1 || opcode == 2 || opcode == 5 || opcode == 7 || opcode == 8 || opcode == 9)) {
            if (nextByte == 0) {
                return getMsgBytes();
            }
        } else if (opcode != null && (opcode == 6 || opcode == 10))
            return getMsgBytes();
        else if (opcode != null && opcode == 4 && len == 4)
            return getMsgBytes();
        else if (opcode != null && opcode == 3)
            if (len == 4)
                dataSize = (short) (((short) bytes[2]) << 8 | (short) (bytes[3]));
            else if (len == dataSize + 6)
                return getMsgBytes();
        return null;
    }

    @Override
    public byte[] encode(byte[] message) {
        return message;
    }

    private void pushByte(byte nextByte) {
        if (len >= bytes.length) {
            bytes = Arrays.copyOf(bytes, len * 2);
        }

        bytes[len++] = nextByte;
    }

    private byte[] getMsgBytes() {
        byte[] ret = Arrays.copyOf(bytes, len);
        len = 0;
        opcode = null;
        return ret;
    }
}
