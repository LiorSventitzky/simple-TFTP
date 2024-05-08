package bgu.spl.net.impl.tftp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.OutputStream;
import java.net.Socket;

public class KeyboardThread implements Runnable {
    IO io;
    CurrState state;
    boolean shouldTerminate = false;
    TftpEncoderDecoder endec;

    public KeyboardThread(IO io, CurrState state, TftpEncoderDecoder endec) {
        this.state = state;
        this.io = io;
        this.endec = endec;
    }

    public void run() {

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String command = null;

        while (!shouldTerminate) {
            try {
                command = reader.readLine();
            } catch (IOException e) {
                System.out.println("IO exception");
            }
            byte[] data = endec.encode(command.getBytes());
            if (data != null) {
                short opcode = bytes2short(data);
                if (changeState(opcode, command))
                    io.send(data);
            } else
                System.out.println("command not valid");

            command = null;
        }
    }

    private boolean changeState(short opcode, String command) {
        if (opcode == 2) { //WRQ
            state.filename = command.substring(4);
            File f = new File("./" + state.filename);
            if (f.exists()) {
                state.write = true;
                state.ack = true;
            } else {
                state.filename = null;
                System.out.println("File does not exists");
                return false;
            }
        }
        if (opcode == 1) { //RRQ
            state.filename = command.substring(4);
            File f = new File("./" + state.filename);
            if (f.exists()) {
                state.filename = null;
                System.out.println("File exists");
                return false;
            } else {
                state.read = true;
                state.ack = true;
            }   
        }
        if (opcode == 6)
            state.dirq = true;
        if (opcode == 10) {
            shouldTerminate = true;
            state.isTerminated = true;
        }
        return true;
    }

    private byte[] short2bytes(short a) {
        return new byte[] { (byte) (a >> 8), (byte) (a & 0xff) };
    }

    private short bytes2short(byte[] message) {
        return (short) (((short) message[0]) << 8 | (short) (message[1]) & 0xFF);
    }

}
