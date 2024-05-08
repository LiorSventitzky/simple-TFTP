package bgu.spl.net.impl.tftp;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Arrays;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;
import bgu.spl.net.srv.StuffToSave;
import bgu.spl.net.srv.StuffToSave;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {

    private boolean shouldTerminate;

    private int connectionId;

    private Connections<byte[]> connections;

    private String username;

    private StuffToSave sts;

    boolean isUploading = false;
    boolean isDownloading = false;
    boolean isDirq = false;

    FileOutputStream fileOs;
    FileInputStream fileIs;
    ByteArrayInputStream dirqIs;
    byte[] currFilename;
    File currFile;

    private final String[] errMsg = { "Not defined",
            "File not found - RRQ DELRQ of non-existing file.",
            "Access violation - File cannot be written, read or deleted.",
            " Disk full or allocation exceeded - No room in disk.",
            "Illegal TFTP operation - Unknown Opcode.",
            " File already exists - File name exists on WRQ.",
            "User not logged in - Any opcode received before Login completes.",
            "User already logged in - Login username already connected." };

    @Override
    public void start(int connectionId, Connections<byte[]> connections, StuffToSave sts) {
        shouldTerminate = false;
        this.connectionId = connectionId;
        this.connections = connections;
        this.sts = sts;
        this.currFilename = null;
    }

    @Override
    public void process(byte[] message) {
        Short opcode = bytes2short(message);

        if (opcode == 7)
            handleLogIn(extractData(message, opcode));
        if (opcode == 8)
            handleDelete(extractData(message, opcode));
        if (opcode == 1)
            handleRead(extractData(message, opcode));
        if (opcode == 2)
            handleWrite(extractData(message, opcode));
        if (opcode == 6)
            handleDirq();
        if (opcode == 3) {
            short size = (short) (((short) message[2]) << 8 | (short) (message[3]) & 0xFF);
            short block = (short) (((short) message[4]) << 8 | (short) (message[5]) & 0xFF);
            handleData(extractData(message, opcode), block, size);
        }
        if (opcode == 4) {
            handleAck(extractData(message, opcode));
        }
        if (opcode == 10) {
            handleDisc();

        }
    }

    private void handleLogIn(byte[] data) {
        String nameInput = new String(data, StandardCharsets.UTF_8);
        if ((username != null && !username.equals(nameInput)) || sts.isUsernameUsed(nameInput))
            sendError(7);
        else {
            if (username == null)
                username = nameInput;
            sts.insert(connectionId, username);
            sendAck(0);
        }
    }

    private void handleDelete(byte[] data) {
        String filename = new String(data, StandardCharsets.UTF_8);
        File f = new File("./Flies/" + filename);
        if (!f.exists() || f.isDirectory())
            sendError(1);
        else if (username == null)
            sendError(6);
        else {
            f.delete();
            sendAck(0);
            sendBcast(true, data);
            sts.updateFileNames();
        }
    }

    private void handleRead(byte[] data) {
        String filename = new String(data, StandardCharsets.UTF_8);
        File f = new File("./Flies/" + filename);
        if (!f.exists() || f.isDirectory())
            sendError(1);
        else if (username == null)
            sendError(6);
        else {
            try {
                fileIs = new FileInputStream(f);
                isDownloading = true;
                byte[] block = { 0, 0 };
                handleAck(block); // to start transfer the data
            } catch (IOException e) {
                System.out.println("IO exception");
            }
        }
    }

    private void handleWrite(byte[] data) {
        String filename = new String(data, StandardCharsets.UTF_8);
        this.currFilename = data;
        File f = new File("./Flies/" + filename);
        this.currFile = f;
        if (f.exists())
            sendError(5);
        else if (username == null)// if unsername=null the clinet hasnt log in yet
            sendError(6);
        else {
            try {
                fileOs = new FileOutputStream(f);
                isUploading = true;
                sendAck(0); // to tell its ok to upload data
            } catch (IOException e) {
                System.out.println("IO exception");

            }
        }

    }

    private void handleDirq() {
        if (username == null) {
            sendError(6);
        } else {
            isDirq = true;
            int byteSize = 0;
            String[] filesNames = sts.getFileNames();
            for (String file : filesNames) {
                byteSize = byteSize + file.length();
            }
            byte[] output = new byte[byteSize + filesNames.length];
            int pos = 0; // saves our position in the byte array
            for (int i = 0; i < filesNames.length; i++) {
                for (byte b : filesNames[i].getBytes()) {
                    output[pos] = b;
                    pos++;
                }
                output[pos] = 0;
                pos++;
            }
            dirqIs = new ByteArrayInputStream(output);
            byte[] block = { 0, 0 };
            handleAck(block); // to start transfer the data
        }
    }

    private void handleData(byte[] message, short block, short size) {
        if (isUploading) {
            try {
                fileOs.write(message);
                fileOs.flush();
                sendAck(block);
                if (size < 512) {
                    sendBcast(false, this.currFilename);
                    this.currFilename = null;
                    fileOs.close();
                    fileOs = null;
                    isUploading = false;
                    this.currFile.createNewFile();
                    sts.updateFileNames();
                }

            } catch (IOException e) {
                System.out.println("IO excpetion");
            }
        } else {
            sendError(2);
        }
    }

    private void handleAck(byte[] data) {
        if (isDownloading) {
            byte[] dataPacket = new byte[518];
            short blockNumber = bytes2short(data);
            blockNumber++;
            byte[] newBlockBytes = short2bytes(blockNumber);

            dataPacket[0] = 0;
            dataPacket[1] = 3;
            dataPacket[4] = newBlockBytes[0];
            dataPacket[5] = newBlockBytes[1];
            try {
                short bytesRead = (short) fileIs.read(dataPacket, 6, 512);
                if (bytesRead == -1) // means that there is nothing to read
                    bytesRead = 0;
                byte[] numReadBytes = short2bytes(bytesRead);
                dataPacket[2] = numReadBytes[0];
                dataPacket[3] = numReadBytes[1];
                if (bytesRead < 512) {
                    dataPacket = Arrays.copyOf(dataPacket, bytesRead + 6);
                    fileIs.close();
                    isDownloading = false;
                }
                connections.send(connectionId, dataPacket);
            } catch (IOException e) {
                System.out.println("IO exception");
            }

        } else if (isDirq) {
            byte[] dataPacket = new byte[518];
            short blockNumber = bytes2short(data);
            blockNumber++;
            byte[] newBlockBytes = short2bytes(blockNumber);

            dataPacket[0] = 0;
            dataPacket[1] = 3;
            dataPacket[4] = newBlockBytes[0];
            dataPacket[5] = newBlockBytes[1];
            try {
                short bytesRead = (short) dirqIs.read(dataPacket, 6, 512);
                if (bytesRead == -1) // means that there is nothing to read
                    bytesRead = 0;
                byte[] numReadBytes = short2bytes(bytesRead);
                dataPacket[2] = numReadBytes[0];
                dataPacket[3] = numReadBytes[1];
                if (bytesRead < 512) {
                    dataPacket = Arrays.copyOf(dataPacket, bytesRead + 6);
                    dirqIs.close();
                    isDirq = false;
                }
                connections.send(connectionId, dataPacket);
            } catch (IOException e) {
                System.out.println("IO exception");

            }
        }
    }

    private void sendError(int code) {
        byte[] output = new byte[5 + errMsg[code].length()];
        output[0] = 0;
        output[1] = 5;

        byte[] errorcode = short2bytes((short) code);
        output[2] = errorcode[0];
        output[3] = errorcode[1];

        output[output.length - 1] = 0;
        byte[] msgB = errMsg[code].getBytes();
        for (int i = 0; i < msgB.length; i++) {
            output[i + 4] = msgB[i];
        }
        connections.send(connectionId, output);
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    public byte[] extractData(byte[] msg, short opcode) {
        byte[] messageBytes = null;
        if (opcode == 1 || opcode == 2 || opcode == 7 || opcode == 8) {
            messageBytes = new byte[msg.length - 3];
            for (int i = 0; i < messageBytes.length; i++) {
                messageBytes[i] = msg[i + 2];
            }

        } else if (opcode == 3) {
            messageBytes = new byte[msg.length - 6];
            for (int i = 0; i < messageBytes.length; i++) {
                messageBytes[i] = msg[i + 6];
            }
        } else if (opcode == 4) {
            messageBytes = new byte[msg.length - 2];
            messageBytes[0] = msg[2];
            messageBytes[1] = msg[3];
        }
        return messageBytes;
    }

    private void sendAck(int block) {
        byte[] blockb = short2bytes((short) block);
        connections.send(connectionId, new byte[] { 0, 4, blockb[0], blockb[1] });
    }

    private void sendBcast(boolean deleted, byte[] data) {
        byte[] output = new byte[4 + data.length];
        output[0] = 0;
        output[1] = 9;
        if (deleted)
            output[2] = 0;
        else
            output[2] = 1;

        output[output.length - 1] = 0;
        for (int i = 0; i < data.length; i++) {
            output[i + 3] = data[i];
        }

        Set<Integer> loggedInIds = sts.getAllIds();
        for (Integer id : loggedInIds) {
            connections.send(id, output);
        }
    }

    private void handleDisc() {
        if (username == null) {
            sendError(6);
        } else {
            sendAck(0);
        }
        sts.remove(connectionId);// from the login map
        connections.disconnect(connectionId); // from connections
    }

    private byte[] short2bytes(short a) {
        return new byte[] { (byte) (a >> 8), (byte) (a & 0xff) };
    }

    private short bytes2short(byte[] message) {
        return (short) (((short) message[0]) << 8 | (short) (message[1]) & 0xFF);
    }

}
