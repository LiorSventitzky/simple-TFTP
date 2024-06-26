package bgu.spl.net.api;

import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.StuffToSave;

public interface BidiMessagingProtocol<T> {
    /**
     * Used to initiate the current client protocol with it's personal connection ID
     * and the connections implementation
     **/
    void start(int connectionId, Connections<T> connections, StuffToSave sts);

    void process(T message);

    /**
     * @return true if the connection should be terminated
     */
    boolean shouldTerminate();
}
