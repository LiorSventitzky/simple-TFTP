package bgu.spl.net.impl.tftp;

import java.util.concurrent.locks.ReadWriteLock;

public class CurrState {
    public volatile boolean isTerminated;
    public volatile boolean dirq;
    public volatile boolean disc;
    public volatile boolean read;
    public volatile boolean write;
    public volatile boolean ack;
    public volatile String filename;

    public CurrState() {
        isTerminated = false;
        dirq = false;
        disc = false;
        ack = false;
        filename = null;
        read = false;
        write = false;
    }

    public void resetAll(){
        dirq = false;
        disc = false;
        ack = false;
        filename = null;
        read = false;
        write = false;
    }

}
