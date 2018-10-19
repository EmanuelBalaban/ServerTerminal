package me.blankboy.remotecommunicationutils;

import android.util.Log;

import java.io.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.DataFormatException;

// Yet just another data representation!
public class DataPackage implements AutoCloseable {
    // Stream stuff!
    private OutputStream _OutputStream = new ByteArrayOutputStream();

    public OutputStream getOutputStream() {
        return _OutputStream;
    }

    public InputStream getInputStream() {
        try {
            return _OutputStream instanceof ByteArrayOutputStream ?
                    new ByteArrayInputStream(((ByteArrayOutputStream) _OutputStream).toByteArray()) :
                    new FileInputStream(((FileOutputStream) _OutputStream).getFD());
        } catch (Exception ignored) {

        }
        return null;
    }

    public long getLength() {
        try {
            return _OutputStream instanceof ByteArrayOutputStream ?
                    ((ByteArrayOutputStream) _OutputStream).toByteArray().length :
                    ((FileOutputStream) _OutputStream).getChannel().size();
        } catch (Exception ignored) {

        }
        return 0;
    }

    @Override
    public void close() throws Exception {
        if (_OutputStream != null) _OutputStream.close();
    }

    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }

    // If confirmation system is enabled checks if all confirmed chunks make Size, otherwise checks if all chunks.Length make Size.
    public boolean isFinished() {
        long sum = 0;
        for (Chunk x : Chunks)
            if (!ConfirmationSystem || x.Confirmation == TypesOConfirmation.Confirmed)
                sum += x.Length;
        return Size == sum;
    }

    // Percentage done of Stream.
    public double Percentage() {
        double result = 0;
        try {
            long sumOLengths = 0; // getLength();
            for (Chunk x : Chunks)
                sumOLengths += x.Length;
            result = (sumOLengths * 100) / Size;
        } catch (Exception ignored) {
        }
        return result;
    }

    // Calculated in dataType/second.
    public double averageSpeed(Data dataType) {
        int multiplier = dataType.getValue();
        int divider = 1;
        while (multiplier > 0) {
            divider *= 1024;
            multiplier--;
        }

        double speed = 0;
        try {
            long sumOLengths = 0;
            long sumOMills = 0;

            for (Chunk x : Chunks){
                sumOLengths += x.Length;
                sumOMills += x.Latency();
            }

            long averageLength = sumOLengths / Chunks.size();
            long averageMills = sumOMills / Chunks.size();

            int time = (int) (averageMills / 1000);
            long length = averageLength / divider;

            speed = length / time;
        } catch (Exception ignored) {

        }
        return speed;
    }

    // Calculated in milliseconds.
    public double averageLatency() {
        long sumOMills = 0;
        for (Chunk x : Chunks)
            sumOMills += x.Latency();
        return Chunks.size() > 0 ? sumOMills / Chunks.size() : -1;
    }

    // First time a chunk was sent.
    public long firstSent() {
        long minVal = Long.MAX_VALUE;
        for (Chunk x : Chunks)
            if (x.Sent < minVal)
                minVal = x.Sent;
        if (minVal == Long.MAX_VALUE) minVal = System.currentTimeMillis();
        return Chunks.size() > 0 ? minVal : -1;
    }

    // Last time a chunk was sent.
    public long lastSent() {
        long maxVal = Long.MIN_VALUE;
        for (Chunk x : Chunks)
            if (x.Sent > maxVal)
                maxVal = x.Sent;
        if (maxVal == Long.MIN_VALUE) maxVal = System.currentTimeMillis();
        return Chunks.size() > 0 ? maxVal : -1;
    }

    // First time a chunk was received.
    public long firstReceived() {
        long minVal = Long.MAX_VALUE;
        for (Chunk x : Chunks)
            if (x.Received < minVal)
                minVal = x.Received;
        if (minVal == Long.MAX_VALUE) minVal = System.currentTimeMillis();
        return Chunks.size() > 0 ? minVal : -1;
    }

    // Last time a chunk was received.
    public long lastReceived() {
        long maxVal = Long.MIN_VALUE;
        for (Chunk x : Chunks)
            if (x.Received > maxVal)
                maxVal = x.Received;
        if (maxVal == Long.MIN_VALUE) maxVal = System.currentTimeMillis();
        return Chunks.size() > 0 ? maxVal : -1;
    }

    byte[] getReadyData(byte[] data, byte StreamCode, byte Code) {
        if (data == null) throw new IllegalArgumentException("Passed data was null!");

        byte[] total = new byte[
                Extensions.LongSize + // Size
                        Extensions.IntSize +  // UniqueID
                        Extensions.ByteSize + // StreamCode
                        Extensions.ByteSize + // UserCode
                        Extensions.LongSize + // SentTime
                        data.length           // Data Length
                ];

        int offset = 0;
        byte[] buffer;

        // Size
        buffer = BitConverter.getBytes((long) data.length);
        System.arraycopy(buffer, 0, total, offset, buffer.length);
        offset += buffer.length;

        // UniqueID
        buffer = BitConverter.getBytes(UniqueID);
        System.arraycopy(buffer, 0, total, offset, buffer.length);
        offset += buffer.length;

        // StreamCode
        total[offset] = StreamCode;
        offset += 1;

        // UserCode
        total[offset] = Code;
        offset += 1;

        // Time
        buffer = BitConverter.getBytes(System.currentTimeMillis());
        System.arraycopy(buffer, 0, total, offset, buffer.length);
        offset += buffer.length;

        // Data
        System.arraycopy(data, 0, total, offset, data.length);

        return total;
    }

    // Gives you chunk byte[] with Chunk.GetHash() and StreamCodes.Confirm, ready to be sent over Socket.
    byte[] getChunkConfirmation(Chunk chunk) {
        if (chunk == null) throw new IllegalArgumentException("Passed Chunk was null!");
        return getReadyData(chunk.getHashData(), StreamCodes.Confirm.getValue(), chunk.Code);
    }

    /// This should be used only in send purposes!
    /// Gives you chunk byte[] with header, ready to be sent over Socket.
    byte[] getChunkData(Chunk chunk) throws IOException {
        if (chunk == null) throw new IllegalArgumentException("Passed Chunk was null!");
        return getReadyData(chunk.readAllBytes(), StreamCode, chunk.Code);
    }

    // All processed chunks are saved here.
    public List<Chunk> Chunks = new ArrayList<Chunk>();

    // Parse data into chunk.
    Chunk parseChunk(byte[] data) throws Exception {
        if (!isValidFormat(data)) throw new DataFormatException("Invalid format!");

        int offset = 0;

        // Size
        long Size = BitConverter.toInt64(data, offset);
        offset += Extensions.LongSize;

        // UniqueID
        int UniqueID = BitConverter.toInt32(data, offset);
        offset += Extensions.IntSize;

        // StreamCode
        byte StreamCode = data[offset];
        offset += Extensions.ByteSize;

        // Code
        byte Code = data[offset];
        offset += Extensions.ByteSize;

        // Time
        long time = BitConverter.toInt64(data, offset);
        offset += Extensions.LongSize;

        // Message(Size - Offset)
        long pos = getLastPosition(false);
        Chunk chunk = new Chunk(this, Code, StreamCode, pos, Extensions.LimitToRange((int) (Size - pos), 0, data.length - offset), time, System.currentTimeMillis());
        chunk.Confirmation = TypesOConfirmation.Confirmed;
        //Log.e("ERROR", "Check this out! DataPackage 177.");
        return chunk;
    }

    // Save a chunk to Chunks array and its data to Stream.
    void saveChunk(Chunk chunk, byte[] data, int offsetInData, boolean calculatePosition, boolean calculateLength) throws Exception {
        if (chunk == null || data == null)
            throw new IllegalArgumentException("Cannot save null objects!");
        if (offsetInData < 0) throw new IllegalArgumentException("Offset cannot be negative.");

        if (calculatePosition)
            chunk.Position = getLastPosition(false); // getLength();
        if (calculateLength)
            chunk.Length = Extensions.LimitToRange((int) (Size - chunk.Position), 0, Buffer);


        //if (chunk.Length != data.Length - offsetInData) throw new InvalidDataException("Chunk length has to be the same with data length - offset.");
        if (Chunks.contains(chunk)) throw new Exception("Chunk was already saved!");

        Chunks.add(chunk);

        _OutputStream.write(data, offsetInData, chunk.Length);
        /*
        Stream.Position = chunk.Position;
        Stream.Write(data, offsetInData, chunk.Length);
        Stream.Flush();
        */
    }

    // Get next chunk to send!
    // Save = true, elevatedSearch = false
    Chunk nextChunk() {
        return nextChunk(true, false);
    }

    Chunk nextChunk(boolean save, boolean elevatedSearch) {
        long position = getLastPosition(true);
        Chunk chunk = new Chunk(this, Code, StreamCode, position, Extensions.LimitToRange((int) (Size - position), 0, Buffer), System.currentTimeMillis(), -1);
        if (save) Chunks.add(chunk);
        return chunk;
    }

    // Get last position from last Chunk.
    long getLastPosition() {
        return getLastPosition(false);
    }

    long getLastPosition(boolean extraSearch) {
        Chunk item = null;
        if (extraSearch) {
            long max = 0;
            for (Chunk x: Chunks)
                if (item.Position > max){
                    item = x;
                    max = item.Position;
                }
        } else if (Chunks.size() > 0) item = Chunks.get(Chunks.size() - 1);

        long position = 0;

        if (item != null)
            position = item.Position + item.Length;

        return position;
    }

    // This is just for receive purposes!
    // CacheFolder = null, writeData = true
    public DataPackage(byte[] data, String CacheFolder, boolean writeData) throws DataFormatException {
        if (!isValidFormat(data)) throw new DataFormatException("Invalid format!");
        int offset = 0;

        // Size
        this.Size = BitConverter.toInt64(data, offset);//BitConverter.ToInt64(data, offset);
        offset += Extensions.LongSize;

        // UniqueID
        this.UniqueID = BitConverter.toInt32(data, offset);//BitConverter.ToInt32(data, offset);
        offset += Extensions.IntSize;

        // StreamCode
        this.StreamCode = data[offset];
        offset += Extensions.ByteSize;

        // Code
        this.Code = data[offset];
        offset += Extensions.ByteSize;

        // Time
        long time = BitConverter.toInt64(data, offset);//BitConverter.ToInt64(data, offset);
        offset += Extensions.LongSize;

        // Message(Size - Offset)
        setCacheFolder(Extensions.getTempDirectory());
        resetStream();

        if (writeData) {
            try {
                _OutputStream.write(data, offset, data.length - offset);
                Chunk chunk = new Chunk(this, Code, StreamCode, 0, data.length - offset, time, System.currentTimeMillis());
                Chunks.add(chunk);
            } catch (IOException ignored) {

            } finally {

            }
        }
    }

    // Create a new DataPackage and use FileStream as source for Stream. For sending purposes.
    public DataPackage(int UniqueID, FileInputStream Stream, byte Code, boolean ConfirmationSystem) throws IOException {
        this.UniqueID = UniqueID;
        this._OutputStream = new FileOutputStream(Stream.getFD());
        this.Size = getLength();
        this.Code = Code;
        this.ConfirmationSystem = ConfirmationSystem;
    }

    // Create a new DataPackage ready to be writen to and sent.
    public DataPackage(int UniqueID, long Size, byte Code, String CacheFolder, boolean ConfirmationSystem) {
        this.UniqueID = UniqueID;
        this.Size = Size;
        this.Code = Code;
        this.ConfirmationSystem = ConfirmationSystem;
        setCacheFolder(Extensions.getTempDirectory());
        resetStream();
    }

    // Expected size of this package.
    private long Size = 0;

    public long getSize() {
        return Size;
    }

    // Might or might not be unique.
    private int UniqueID = -1;

    public int getUniqueID() {
        return UniqueID;
    }

    // In case of buffering it is going to be saved here.
    private String Path = null;

    public String getPath() {
        return Path;
    }

    // Expected size is bigger than allowed MaximumUnBuffered. This indicates it is being saved to a temporary file.
    //private boolean _IsBuffered = false;

    public boolean isBuffered() {
        return Size > MaximumUnBuffered || _OutputStream instanceof FileOutputStream;
        //return _IsBuffered;
    }

    // This package stream code. Usually StreamCodes.Append.
    byte StreamCode = StreamCodes.Append.getValue();

    // This is user very own code descriptor.
    private byte Code = 0;

    public byte getCode() {
        return Code;
    }

    public byte[] getHashData() throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        InputStream is = getInputStream();
        byte[] buffer = new byte[Buffer];
        int read;
        while ((read = is.read(buffer)) != -1) {
            md.update(buffer, 0, read);
        }
        return md.digest();
    }

    // Get MD5 hash of this package.
    // It calculates hash of current stream data.
    public String getHash() throws Exception {
        return Extensions.getReadableHexadecimal(getHashData());
    }

    // Don't want this package to be confirmed? Want faster transmission?
    public boolean ConfirmationSystem = true;

    // Confirm a chunk with its hash.
    // This method is automatic, so a false result means invalid hash, or nothing to confirm.
    boolean confirmChunk(byte[] data, int offset, int count) throws Exception {
        if (data == null) throw new IllegalArgumentException("Data array was null!");
        if (offset < 0 || count < 0)
            throw new IndexOutOfBoundsException("Offset and count cannot be negative!");
        if (offset + count > data.length)
            throw new IndexOutOfBoundsException("Offset + count cannot be bigger than data.Length!");

        if (!ConfirmationSystem) throw new Exception("Confirmation system is not enabled!");

        byte[] hash = new byte[count];
        System.arraycopy(data, offset, hash, 0, count);

        for (Chunk x: Chunks)
            if (x.Confirmation == TypesOConfirmation.NotConfirmed)
                if (Arrays.equals(x.getHashData(), hash)) {
                    x.Confirmation = TypesOConfirmation.Confirmed;
                    x.Received = System.currentTimeMillis();
                    return true;
                }

        return false;
    }

    // Using this the sending queue processor determinates the next message that will be sent.
    public TypesOPriority Priority = TypesOPriority.Normal;

    // If this package length is bigger, it will be buffered.
    public static final int MaximumUnBuffered = 4096;

    // Default buffer for normal messages transmission.
    public static final int DefaultBuffer = 4096;

    // Default offset of message itself on encoding and decoding.
    public static final int DefaultMessageOffset = Extensions.LongSize * 2 + Extensions.IntSize + Extensions.ByteSize * 2;

    // Wanna set you own buffer size?!
    public int Buffer = DefaultBuffer;

    // This tells you if is valid package. Means if bigger or equal to DefaultMessageOffset.
    public static boolean isValidFormat(byte[] data) {
        return data.length >= DefaultMessageOffset;
    }

    String _CacheFolder = "";//System.IO.Path.GetTempPath();

    // This is where temporary files are being stored.
    // By default this equals Path.GetTempPath().
    public String getCacheFolder() {
        return _CacheFolder;
    }

    public void setCacheFolder(String value) {
        if (value == null) value = Extensions.getTempDirectory();

        File dir = new File(value);

        if (dir.exists() && dir.isDirectory())
            _CacheFolder = value;
        else
            _CacheFolder = Extensions.getTempDirectory();
    }

    // Reset entire stream. Be careful with this! it is not for kids!
    public void resetStream() {
        try {
            File f = new File(Path);
            if (f.exists()) f.delete();
            Path = null;
            if (Size > MaximumUnBuffered) {
                File tmp = File.createTempFile("blankboy", ".tmp", new File(_CacheFolder));
                Path = tmp.getAbsolutePath();
                _OutputStream = new FileOutputStream(tmp);
            } else _OutputStream = new ByteArrayOutputStream();
        } catch (Exception ignored) {

        }
    }

    /// Get human readable version of this package.
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();

        sb.append("{");
        sb.append("UniqueID = " + UniqueID);
        sb.append("Size = " + Size);
        sb.append("Buffer = " + Buffer);
        sb.append("Chunks = " + Chunks.size());

        sb.append("\nFirstSent = " + firstSent());
        sb.append("FirstReceived = " + firstReceived());
        sb.append("LastSent = " + lastSent());
        sb.append("LastReceived = " + lastReceived());

        sb.append("\nIsBuffered = " + isBuffered());
        if (isBuffered()) sb.append("TempPath = " + Path);
        sb.append("IsFinished = " + isFinished());
        if (!isFinished()) {
            sb.append("PercentageDone = " + Percentage());
            sb.append("Stream = " + getLength());
        } else {
            try {
                sb.append("Hash = " + getHash());
            } catch (Exception ignored) {

            }
        }

        sb.append("\nCode = " + Code);
        sb.append("StreamCode = " + StreamCode);

        sb.append("\nPriority = " + Priority);
        sb.append("ConfirmationSystem = " + ConfirmationSystem);

        sb.append("\n --- Average Values --- ");
        sb.append("Latency = " + averageLatency());
        sb.append("Speed = " + averageSpeed(Data.KiloBytes));
        sb.append("}");

        return sb.toString();

    }
}