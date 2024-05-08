package bgu.spl.net.impl.tftp;

import bgu.spl.net.srv.Server;
import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.api.BidiMessagingProtocol;

public class TftpServer {

    public static void main(String[] args) {

        // you can use any server...
        Server.threadPerClient(
                7777, // port
                () -> new TftpProtocol(), // protocol factory
                TftpEncoderDecoder::new // message encoder decoder factory
        ).serve();

    }
}
