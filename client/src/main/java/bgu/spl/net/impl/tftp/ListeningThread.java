package bgu.spl.net.impl.tftp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class ListeningThread implements Runnable {
    IO io;
    CurrState state;
    boolean shouldTerminate = false;
    TftpEncoderDecoder encdec;
    FileOutputStream fileOut;
    FileInputStream fileIn;
    byte[] allNames = null;
    boolean finishUpload = false;

    public ListeningThread(IO io, CurrState state, TftpEncoderDecoder encdec) {
        this.state = state;
        this.io = io;
        this.encdec = encdec;
    }

    public void run() {
        int read;
        while (!shouldTerminate) {
            read = io.receive();
            byte[] nextMessage = encdec.decodeNextByte((byte) read);
            if (nextMessage != null) {
                short opcode = bytes2short(nextMessage, 0, 1);
                if (opcode == 4)
                    handleAck(nextMessage);
                if (opcode == 5)
                    handleError(nextMessage);
                if (opcode == 3)
                    handleData(nextMessage);
                if (opcode == 9) {
                    handleBcast(nextMessage);
                }
            }
        }
    }

    private void handleAck(byte[] message) {
        printAck(message);
        if (finishUpload) {
            System.out.println("WRQ " + state.filename + " complete");
            finishUpload = false;
            state.filename = null;
        }
        if (state.ack && state.write) {
            byte[] data = dataToUpload(message);
            if (data != null)
                io.send(data);
        }
        if (state.isTerminated) {
            shouldTerminate = true;
            state.disc = false;
            io.close();
        }

    }

    private byte[] dataToUpload(byte[] ack) {// for WRQ
        short blockNumber = bytes2short(ack, 2, 3);
        if (blockNumber == 0) {
            try {
                File f = new File("./" + state.filename);
                fileIn = new FileInputStream(f);
            } catch (FileNotFoundException e) {

            }
        }
        if (fileIn != null) {
            byte[] dataPacket = new byte[518];
            blockNumber++;
            byte[] newBlockBytes = short2bytes(blockNumber);

            dataPacket[0] = 0;
            dataPacket[1] = 3;
            dataPacket[4] = newBlockBytes[0];
            dataPacket[5] = newBlockBytes[1];
            try {
                short bytesRead = (short) fileIn.read(dataPacket, 6, 512);
                if (bytesRead == -1) // means that there is nothing to read
                    bytesRead = 0;
                byte[] numReadBytes = short2bytes(bytesRead);
                dataPacket[2] = numReadBytes[0];
                dataPacket[3] = numReadBytes[1];
                if (bytesRead < 512) {
                    dataPacket = Arrays.copyOf(dataPacket, bytesRead + 6);
                    fileIn.close();
                    fileIn = null;
                    state.write = false;
                    state.ack = false;
                    finishUpload = true;
                }
            } catch (IOException e) {
                System.out.println("IO exception");
            }
            return dataPacket;
        }
        return null;
    }

    private void handleData(byte[] nextMessage) {
        if (state.read) {
            if (state.ack) {
                File newFile = new File("./" + state.filename);
                try {
                    newFile.createNewFile();
                    fileOut = new FileOutputStream(newFile);
                } catch (IOException E) {
                    System.out.println("IO exception");
                }
                state.ack = false;
            }
            short size = bytes2short(nextMessage, 2, 3);
            short block = bytes2short(nextMessage, 4, 5);
            byte[] fileData = copyPartArray(nextMessage, 6, 6);
            try {
                fileOut.write(fileData);
                fileOut.flush();
                sendAck(block);
                if (size < 512) {
                    state.read = false;
                    fileOut.close();
                    fileOut = null;
                    System.out.println("RRQ " + state.filename + " complete");
                    state.filename = null;
                }
            } catch (IOException e) {
                System.out.println("IO exception");
            }

        }
        if (state.dirq) {
            short size = bytes2short(nextMessage, 2, 3);
            short block = bytes2short(nextMessage, 4, 5);
            byte[] data = copyPartArray(nextMessage, 6, 6);
            addAllNames(data); // for case when there is more than 1 data package
            if (data.length < 512) {
                String name = "";
                for (int i = 0; i < allNames.length; i++) {
                    if (allNames[i] == 0) {
                        System.out.println(name);
                        name = "";
                    } else {
                        name = name + (char) allNames[i];
                    }
                }
                allNames = null;
            }
        }
    }

    private void addAllNames(byte[] data) { // add new file names to the total array
        if (allNames == null)
            allNames = data;
        else {
            byte[] temp = allNames;
            allNames = new byte[temp.length + data.length];
            for (int i = 0; i < temp.length; i++) {
                allNames[i] = temp[i];
            }
            for (int i = 0; i < data.length; i++) {
                allNames[i + temp.length] = data[i];
            }
        }
    }

    private void handleBcast(byte[] nextMessage) {
        byte[] filenameBytes = copyPartArray(nextMessage, 4, 3);
        String filename = new String(filenameBytes, StandardCharsets.UTF_8);
        if (nextMessage[2] == 0)
            System.out.println("BCAST del " + filename);
        else
            System.out.println("BCAST add " + filename);
    }

    private void sendAck(short block) {
        byte[] ack = new byte[4];
        ack[0] = 0;
        ack[1] = 4;
        byte[] blockByte = short2bytes(block);
        ack[2] = blockByte[0];
        ack[3] = blockByte[1];
        io.send(ack);
    }

    private void printAck(byte[] message) {
        System.out.println("ACK:" + bytes2short(message, 2, 3));
    }

    private void handleError(byte[] message) {
        printError(message);
        if (state.isTerminated)
            shouldTerminate = true;
        state.resetAll();
    }

    private void printError(byte[] message) {
        byte[] errMsg = copyPartArray(message, 5, 4);
        String error = new String(errMsg, StandardCharsets.UTF_8);
        System.out.println("Error " + bytes2short(message, 2, 3) + ": " + error);
    }

    private byte[] copyPartArray(byte[] arr, int sizedif, int start) {
        byte[] output = new byte[arr.length - sizedif];
        for (int i = 0; i < output.length; i++) {
            output[i] = arr[i + start];
        }
        return output;
    }

    private byte[] short2bytes(short a) {
        return new byte[] { (byte) (a >> 8), (byte) (a & 0xff) };
    }

    private short bytes2short(byte[] message, int start, int finish) {
        return (short) (((short) message[start]) << 8 | (short) (message[finish]) & 0xFF);
    }
}
