package bgu.spl.net.srv;

import java.io.IOException;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T> {
    private ConcurrentHashMap<Integer, ConnectionHandler<T>> connectionsMap;

    public ConnectionsImpl() {
        connectionsMap = new ConcurrentHashMap<>();
    }

    @Override
    public void connect(int connectionId, ConnectionHandler<T> handler) {
        if (!connectionsMap.containsKey(connectionId))
            connectionsMap.put((Integer) connectionId, handler);
    }

    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = connectionsMap.get(connectionId);
        if (handler != null) {
            handler.send(msg);
            return true;
        }
        return false;
    }

    @Override
    public void disconnect(int connectionId) {
        if (connectionsMap.containsKey(connectionId)) {
            try {
                connectionsMap.get(connectionId).close();
                connectionsMap.remove(connectionId);
            } catch (IOException e) {
                System.out.println("IO excpetion");
            }
        }

    }

}
