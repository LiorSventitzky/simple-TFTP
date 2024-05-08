package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class IO {
    Socket socket;
    BufferedInputStream in;
    BufferedOutputStream out;

    public IO(Socket socket) {
        this.socket = socket;
        try {
            in = new BufferedInputStream(socket.getInputStream());
            out = new BufferedOutputStream(socket.getOutputStream());

        } catch (IOException e) {
            System.out.println("IO exception");
        }
    }

    public synchronized void send(byte[] data) {
        try {
            out.write(data);
            out.flush();
        } catch (IOException e) {
            System.out.println("IO exception");
        }
    }

    public byte receive() {
        byte read = 0;
        try {
            read = (byte) in.read();
        } catch (IOException e) {
            System.out.println("IO exception");
        }
        return read;
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            System.out.println("IO excpetion");
        }
    }
}
