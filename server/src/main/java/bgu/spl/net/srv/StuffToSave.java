package bgu.spl.net.srv;

import java.util.Set;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StuffToSave<T> {
    private ConcurrentHashMap<Integer, String> logMap;
    private String[] fileNames;

    public StuffToSave() {
        this.logMap = new ConcurrentHashMap<Integer, String>();
        updateFileNames();
    }

    public void insert(int connectionId, String username) {
        if (!logMap.containsKey(connectionId))
            logMap.put((Integer) connectionId, username);
    }

    public void remove(int connectionId) {
        if (logMap.containsKey(connectionId))
            logMap.remove(connectionId);
    }

    public boolean isUsernameUsed(String username) {
        return (logMap.contains(username));
    }

    public Set<Integer> getAllIds() {
        return logMap.keySet();
    }

    public void updateFileNames() {
        File directory = new File("./Flies");
        fileNames = directory.list();
    }

    public String[] getFileNames() {
        return fileNames;
    }
}
