package bgu.spl.net.impl.tftp;

import java.nio.charset.StandardCharsets;
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
                && (opcode == 1 || opcode == 2 || opcode == 7 || opcode == 8)) {
            if (nextByte == 0) {
                return getMsgBytes();
            }

        } else if (opcode != null && (opcode == 6 || opcode == 10))
            return getMsgBytes();

        else if (opcode != null && opcode == 4 && len == 4)
            return getMsgBytes();

        else if (opcode != null && (opcode == 5 && len >= 5)) {
            if (nextByte == 0) {
                return getMsgBytes();
            }
        } else if (opcode != null && (opcode == 9 && len >= 4)) {
            if (nextByte == 0) {
                return getMsgBytes();
            }

        } else if (opcode != null && opcode == 3) {
            if (len == 4) {
                dataSize = (short) (((short) bytes[2]) << 8 | (short) (bytes[3]) & 0xFF);
            } else if (len == dataSize + 6) {
                return getMsgBytes();
            }
        }
        return null;
    }

    @Override
    public byte[] encode(byte[] message) {
        String data = new String(message, StandardCharsets.UTF_8);
        String[] words = data.split(" ");
        short opcode = findOpcode(words[0]);
        if (opcode == 0)
            return null;
            
        if (opcode == 1 || opcode == 2 || opcode == 7 || opcode == 8 || opcode == 9) {
            if(words.length==1)
               return null;
            byte[] filename = data.substring(words[0].length() + 1).getBytes();
            byte[] output = new byte[filename.length + 3];
            byte[] b = short2bytes(opcode);
            output[0] = b[0];
            output[1] = b[1];
            output[output.length - 1] = 0;
            for (int i = 0; i < filename.length; i++) {
                output[i + 2] = filename[i];
            }
            return output;
        } else if (opcode == 6 || opcode == 10)
            return short2bytes(opcode);
        return null;
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

    public short findOpcode(String code) {
        if (code.equals("RRQ"))
            return 1;
        if (code.equals("WRQ"))
            return 2;
        if (code.equals("DIRQ"))
            return 6;
        if (code.equals("LOGRQ"))
            return 7;
        if (code.equals("DELRQ"))
            return 8;
        if (code.equals("DISC"))
            return 10;

        return 0;
        // retuen not valid opcode
    }

    private byte[] short2bytes(short a) {
        return new byte[] { (byte) (a >> 8), (byte) (a & 0xff) };
    }

    private short bytes2short(byte[] message) {
        return (short) (((short) message[0]) << 8 | (short) (message[1]) & 0xFF);
    }
}
