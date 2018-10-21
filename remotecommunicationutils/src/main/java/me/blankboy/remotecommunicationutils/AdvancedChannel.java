package me.blankboy.remotecommunicationutils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;

// Use this and thank me later, or you can thank me now in advance :P.
// This provides Connection extra fundamental methods and ensures there is no data loss.
public class AdvancedChannel implements AutoCloseable, ConnectionListener {
    // Think you lose this channel?
    private int UniqueID = -1;

    public int getUniqueID() {
        return UniqueID;
    }

    // Interface operations
    private List<ChannelListener> Listeners = new ArrayList<>();

    public void addListener(ChannelListener listener) {
        if (!Listeners.contains(listener)) Listeners.add(listener);
    }

    private void onExceptionOccurred(Exception ex) {
        for (ChannelListener l : Listeners)
            l.onExceptionOccurred(ex, this);
    }

    private void onDataReceived(DataPackage data, Chunk chunk) {
        for (ChannelListener l : Listeners)
            l.onDataReceived(data, chunk, this);
    }

    private void onDataConfirmed(DataPackage data, Chunk chunk){
        for (ChannelListener l : Listeners)
            l.onDataConfirmed(data, chunk, this);
    }

    private void broadcastException(Exception ex) {
        Console.Log(ex.toString(), TypesOLog.ERROR);
        onExceptionOccurred(ex);
    }

    // This is useless.
    private Connection Connection;

    public Connection getConnection() {
        return Connection;
    }

    // Just basic console.
    private Logger Console;

    public Logger getConsole() {
        return Console;
    }

    // Create new advanced channel and Initialize it.
    public AdvancedChannel(Connection connection, File CacheFolder) throws IOException {
        setCacheFolder(CacheFolder);
        this.Connection = connection;
        InitializeChannel();
    }

    @Override
    public void close() throws Exception {
        if (Connection != null) Connection.close();
    }

    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }

    private void InitializeChannel() {
        if (Connection == null)
            throw new NullPointerException("Connection was null. Cannot initialize null object.");
        Connection.addListener(this);
        Console = Connection.getConsole();
        UniqueID = Connection.getUniqueID();
    }

    @Override
    public void onDataReceived(byte[] dataA, Date receivedA, Connection sender) {
        final byte[] thread = dataA;
        final Date threadReceived = receivedA;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] data = thread;
                Date received = threadReceived;
                try {
                    //Console.Log(Arrays.toString(data), TypesOLog.DEBUG);

                    if (DataPackage.isValidFormat(data)) {
                        //Console.Log("Received valid format data!", TypesOLog.DEBUG);

                        DataPackage justForCheck = new DataPackage(data, CacheFolder, false);
                        //Console.Log("Parsed data into DataPackage!", TypesOLog.DEBUG);

                        StreamCodes Code = StreamCodes.valueOf(justForCheck.StreamCode);
                        //Console.Log(justForCheck.toString(), TypesOLog.DEBUG);

                        if (Code == StreamCodes.Confirm) {
                            DataPackage dp = null;
                            for (DataPackage d : OutgoingQueue) {
                                //Console.Log("Searching ID = " + d.getUniqueID(), TypesOLog.DEBUG);
                                if (d.getUniqueID() == justForCheck.getUniqueID()) {
                                    dp = d;
                                    break;
                                }
                            }
                            if (dp != null) {

                                //Console.Log("Attempting to confirm package with id: " + dp.getUniqueID(), TypesOLog.DEBUG);
                                boolean result = dp.confirmChunk(data, DataPackage.DefaultMessageOffset, data.length - DataPackage.DefaultMessageOffset);
                                if (result)
                                {
                                    //Console.Log("Confirmed package with id: " + dp.getUniqueID(), TypesOLog.DEBUG);
                                    onDataConfirmed(dp, dp.Chunks.get(dp.Chunks.size() - 1));
                                }
                                else
                                    //Console.Log("Couldn't confirm package with id: " + dp.getUniqueID(), TypesOLog.DEBUG);
                                if (dp.isFinished()) OutgoingQueue.remove(dp);
                            }
                        } else {
                            DataPackage dp = null;
                            for (DataPackage x: IncomingPackages)
                                if (x.getUniqueID() == justForCheck.getUniqueID() && x.getSize() == justForCheck.getSize()) {
                                    dp = x;
                                    break;
                                }

                            if (dp == null) {
                                dp = new DataPackage(data, CacheFolder, false);
                                IncomingPackages.add(dp);
                                reportID(dp.getUniqueID());
                            }

                            //Console.Log("Got DataPackage to save to!", TypesOLog.DEBUG);

                            Chunk chunk = dp.parseChunk(data);
                            dp.saveChunk(chunk, data, DataPackage.DefaultMessageOffset, false, false);

                            if (Code == StreamCodes.Append) {
                                // Faster will be to use normal method and chunk.GetHashData()
                                //Console.Log("Sending confirmation data...", TypesOLog.DEBUG);

                                byte[] hash = null;

                                try {
                                    MessageDigest md = MessageDigest.getInstance("MD5");
                                    md.update(data, DataPackage.DefaultMessageOffset, data.length - DataPackage.DefaultMessageOffset);
                                    hash = md.digest();
                                } catch (Exception ignored) {
                                }

                                DataPackage dpa = new DataPackage(dp.getUniqueID(), hash.length, (byte) 0, CacheFolder, false);
                                dpa.getOutputStream().write(hash, 0, hash.length);
                                dpa.StreamCode = StreamCodes.Confirm.getValue();
                                dpa.Priority = TypesOPriority.VeryHigh;
                                dpa.ConfirmationSystem = false;
                                send(dpa);
                            }

                            if (dp.isFinished()) {
                                IncomingPackages.remove(dp);
                            }

                            onDataReceived(dp, chunk);
                        }
                    } else {
                        // Drop connection
                        Console.Log("Not valid package! Dropping connection!");
                        Connection.disconnect();
                    }
                } catch (Exception ex) {
                    broadcastException(ex);
                }
            }
        });
        t.start();
    }

    @Override
    public void onStatusChanged(Connection.StatusType status, Connection sender) {
        if (status == me.blankboy.remotecommunicationutils.Connection.StatusType.DISCONNECTED)
            Console.Log("Connection lost!", TypesOLog.WARN);
    }

    @Override
    public void onExceptionOccurred(Exception ex, Connection sender) {
        onExceptionOccurred(ex);
    }

    // ID Allocation System
    private List<Integer> InServiceIDs = new ArrayList<>();

    // Wanna do things on your own?
    public int allocateNewID() {
        int id = InServiceIDs.size();
        Random rd = new Random();
        while (InServiceIDs.contains(id)) id = rd.nextInt();
        InServiceIDs.add(id);
        return id;
    }

    private void reportID(int UniqueID) {
        if (!InServiceIDs.contains(UniqueID)) InServiceIDs.add(UniqueID);
    }

    // Check if the underlying connection is still connected.
    public boolean isStillConnected() {
        if (Connection == null) return false;
        return Connection.isStillConnected();
    }

    // Don't want any package to be confirmed? Want faster transmission?
    public boolean ConfirmationSystem = true;

    // This is where temporary files are being stored.
    public String CacheFolder = null;
    public void setCacheFolder(File cacheFolder) throws IOException {
        if (!cacheFolder.exists()) cacheFolder.mkdirs();
        CacheFolder = cacheFolder.getCanonicalPath();
    }

    // Send a file! Actually it creates a new DataPackage with specified file stream.
    // code = 4, buffer = 4096 * 1000, awaitConfirmation = true
    public int sendFile(File fileToSend, byte code, int buffer, boolean awaitConfirmation) throws IOException, FileNotFoundException {
        if (!fileToSend.exists()) throw new FileNotFoundException();
        int UniqueID = allocateNewID();
        FileInputStream fs = new FileInputStream(fileToSend);
        DataPackage dp = new DataPackage(UniqueID, fs, code, ConfirmationSystem && awaitConfirmation);
        dp.Buffer = buffer;
        send(dp);
        return UniqueID;
    }
    public int sendFile(File fileToSend, byte code, int buffer) throws IOException {
        return sendFile(fileToSend, code, buffer, ConfirmationSystem);
    }

    // Send a simple message! (or write message data to new DataPackage -> stream)
    // code = 0, awaitConfirmation = true
    public int sendMessage(String message, byte code, boolean awaitConfirmation) {
        int uniqueID = allocateNewID();
        byte[] data = message.getBytes();
        DataPackage dp = null;
        try {
            dp = new DataPackage(uniqueID, data.length, code, CacheFolder, ConfirmationSystem && awaitConfirmation);
            dp.getOutputStream().write(data, 0, data.length);
        } catch (IOException ignored) {
        }
        send(dp);
        return uniqueID;
    }
    public int sendMessage(String message, byte code){
        return sendMessage(message, code, ConfirmationSystem);
    }

    // Send DataPackage over the stream.
    public void send(DataPackage dp) {
        if (dp == null) throw new IllegalArgumentException("DataPackage was null!");
        reportID(dp.getUniqueID());
        //if (!StreamSupport.stream(OutgoingQueue).anyMatch(x -> x.getUniqueID() == dp.getUniqueID()))
        if (!OutgoingQueue.contains(dp)) OutgoingQueue.add(dp);
        QueueProcessor();
    }

    private boolean availableToSend(){
        for (DataPackage x: OutgoingQueue)
            if (x.getLastPosition() < x.getSize()) // !x.isFinished()
                return true;
        return false;
    }
    private void QueueProcessor() {
        if (QueueProcessorStatus == ProcessorState.Working) return;

        Console.Log("Queue processor has started working!", TypesOLog.DEBUG);
        QueueProcessorStatus = ProcessorState.Working;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                DataPackage last = null;
                while (isStillConnected() && availableToSend()) {
                    System.gc();
                    try {
                        if (last != null && last.getLastPosition() == last.getSize()) last = null;

                        DataPackage current = null;

                        if (QueueRule == TypesORules.Normal) {
                            for (DataPackage x: OutgoingQueue)
                                if (x.Priority == TypesOPriority.VeryHigh){
                                    current = x;
                                    break;
                                }
                            if (current == null){
                                if (last == null){
                                    for (DataPackage x: OutgoingQueue)
                                        if (x.Priority == TypesOPriority.High)
                                        {
                                            current = x;
                                            break;
                                        }
                                }
                                else current = last;
                            }
                            if (current == null)
                                for (DataPackage x: OutgoingQueue)
                                    if (x.Priority == TypesOPriority.Normal)
                                    {
                                        current = x;
                                        break;
                                    }
                        } else {
                            for (DataPackage x: OutgoingQueue)
                                if (x.Priority == TypesOPriority.VeryHigh)
                                    current = x;
                            if (current == null){
                                if (last == null){
                                    for (DataPackage x: OutgoingQueue)
                                        if (x.Priority == TypesOPriority.High)
                                            current = x;
                                }
                                else current = last;
                            }
                            if (current == null)
                                for (DataPackage x: OutgoingQueue)
                                    if (x.Priority == TypesOPriority.Normal)
                                        current = x;
                        }

                        if (current != null) {
                            Console.Log("Processing package with id: " + current.getUniqueID(), TypesOLog.DEBUG);
                            Console.Log(current.toString(), TypesOLog.DEBUG);

                            int attemptCount = 0;

                            Chunk chunk = current.nextChunk();
                            chunk.Sent = System.currentTimeMillis();
                            byte[] buffer = current.getChunkData(chunk);

                            do {
                                //System.gc();
                                attemptCount++;
                                Console.Log("Attempting to send: " + String.valueOf(attemptCount), TypesOLog.DEBUG);

                                Connection.Send(buffer, 0, buffer.length);

                                Console.Log("Sent!", TypesOLog.DEBUG);

                                long now = System.currentTimeMillis();
                                while (current.ConfirmationSystem && chunk.Confirmation == TypesOConfirmation.NotConfirmed &&
                                        System.currentTimeMillis() - now <= 5000)
                                    Thread.sleep(100);

                            }
                            while (attemptCount < 3 && current.ConfirmationSystem && chunk.Confirmation == TypesOConfirmation.NotConfirmed);

                            if (current.ConfirmationSystem && chunk.Confirmation == TypesOConfirmation.NotConfirmed) {
                                Console.Log("Unable to process package '" + current.getUniqueID() + "'. " + String.valueOf(current.getSize() - (chunk.Position + chunk.Length)) + " bytes left!", TypesOLog.DEBUG);
                                current.close();
                                OutgoingQueue.remove(current);
                            } else {
                                //current.Position = current.Stream.Position;
                                //onDataConfirmed(current, chunk);

                                if (current.isFinished()) {
                                    Console.Log("Successfully processed package with id: " + current.getUniqueID(), TypesOLog.DEBUG);
                                    current.getOutputStream().flush();
                                    if (current.isBuffered()) current.getOutputStream().close();
                                    OutgoingQueue.remove(current);
                                } else if (current.Priority == TypesOPriority.Normal)
                                    last = current;
                            }
                        } else OutgoingQueue.removeAll(Collections.singleton(null));
                    } catch (Exception ex) {
                        broadcastException(ex);
                    }
                }

                QueueProcessorStatus = ProcessorState.Stopped;
                Console.Log("Queue processor has finished working!", TypesOLog.DEBUG);
            }
        });
        t.start();
    }

    // All in-receive packages on the line.
    public List<DataPackage> IncomingPackages = new ArrayList<>();

    // All out-going packages in one list. Position and priority counts!
    public List<DataPackage> OutgoingQueue = new ArrayList<>();

    // Outgoing queue processor. Indicates value 'Working' only when working.
    private ProcessorState QueueProcessorStatus = ProcessorState.Stopped;

    public ProcessorState getQueueProcessorStatus() {
        return QueueProcessorStatus;
    }

    // This is the rule on which outgoing queue is processed.
    public TypesORules QueueRule = TypesORules.Normal;
}
