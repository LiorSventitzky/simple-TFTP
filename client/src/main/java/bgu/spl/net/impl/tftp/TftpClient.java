package bgu.spl.net.impl.tftp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class TftpClient {

    public static void main(String[] args) throws IOException {

        if (args.length == 0) {
            args = new String[] { "localhost", "7777" };
        }

        if (args.length < 2) {
            System.out.println("you must supply two arguments: host, port");
            System.exit(1);
        }

        CurrState state = new CurrState();
        TftpEncoderDecoder encdec = new TftpEncoderDecoder();

        try {
            Socket sock = new Socket(args[0], Integer.parseInt(args[1]));
            IO io = new IO(sock);
            System.out.println("connected to server");
            KeyboardThread kbThread = new KeyboardThread(io, state, encdec);
            ListeningThread lsThread = new ListeningThread(io, state, encdec);
            Thread kb = new Thread(kbThread);
            Thread ls = new Thread(lsThread);
            kb.start();
            ls.start();

            try {
                kb.join();
                ls.join();
            } catch (InterruptedException e) {
                System.out.println("main thread interrupted");
            }

        } catch (IOException E) {
            System.out.println("IO exception");
        }

    }
}
