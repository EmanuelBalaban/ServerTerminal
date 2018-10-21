package me.blankboy.remotecommunicationutils;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.Date;

final class Extensions {
    public static int getUnsignedByte(byte value){
        return value & 0xff;
    }
    public static byte getSignedByte(int value){
        return (byte) value;
    }

    /*
    public static byte[] getBytes(int value){
        byte[] buffer = new byte[IntSize];
        for (int i = 0; i < buffer.length; i++)
            buffer[i] = (byte)(value >> 8 * i);
        return buffer;
    }
    public static byte[] getBytes(long value)
    {
        byte[] buffer = new byte[LongSize];
        for (int i = 0; i < buffer.length; i++)
            buffer[i] = (byte)(value >> 8 * i);
        return buffer;
    }

    public static int getInt(byte[] value, int offset){
        //return ByteBuffer.wrap(value, offset, IntSize).getInt();
        int result = 0;
        if (value != null){
            result += (value[offset + 3] & 0x000000FF) << 24;
            result += (value[offset + 2] & 0x000000FF) << 16;
            result += (value[offset + 1] & 0x000000FF) << 8;
            result += (value[offset + 0] & 0x000000FF);
        }
        return result;
    }
    public static long getLong(byte[] value, int offset)
    {
        byte[] data = new byte[LongSize];
        System.arraycopy(value, offset, data, 0, data.length);

        ByteBuffer buffer = ByteBuffer.allocate(data.length);
        buffer.put(data);
        buffer.flip();//need flip

        return buffer.getLong();
        /*
        long result = 0;
        for (int i = offset; i < offset + LongSize; i++)
            result |= (long) value[i] << 8 * i;
        return result;
        *
    }
    */

    public static final int LongSize = 8;//Long.SIZE / Byte.SIZE;
    public static final int IntSize = 4;//Integer.SIZE / Byte.SIZE;
    public static final int ByteSize = 1;//Byte.SIZE / Byte.SIZE;

    // Read from network stream until fill specified byte array.
    public static void ReadStream(InputStream reader, Object dataObj, int length) throws IOException {
        byte[] data = (byte[]) dataObj;
        if (data == null) data = new byte[length];

        int offset = 0;
        int remaining = length;
        while (remaining > 0) {
            int read = reader.read(data, offset, remaining);
            if (read <= 0)
                throw new EOFException
                        (String.format("End of stream reached with %d bytes left to read", remaining));
            remaining -= read;
            offset += read;
        }
        dataObj = data;
    }

    // Limit value to specified range. It keeps the value, no conversion.
    public static long LimitToRange(long value, long inclusiveMinimum, long inclusiveMaximum)
    {
        if (value < inclusiveMinimum) { return inclusiveMinimum; }
        if (value > inclusiveMaximum) { return inclusiveMaximum; }
        return value;
    }

    // Limit value to specified range. It keeps the value, no conversion.
    public static int LimitToRange(int value, int inclusiveMinimum, int inclusiveMaximum)
    {
        if (value < inclusiveMinimum) { return inclusiveMinimum; }
        if (value > inclusiveMaximum) { return inclusiveMaximum; }
        return value;
    }

    // private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String getReadableHexadecimal(byte[] bytes) {
        /*
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
        */
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes)
            sb.append(String.format("%02X", b));
        return sb.toString();
    }
    /*
    public static String getReadableHexadecimal(byte[] value)
    {
        StringBuilder sBuilder = new StringBuilder();
        for (int i = 0; i < value.length; i++)
            sBuilder.append(String.format("x2", value[i]));
        return sBuilder.toString();
    }
    */
/*
    public static String getTempDirectory() {

        try {
            File temp = File.createTempFile("temp-file-name", ".tmp");
            String absolutePath = temp.getAbsolutePath();
            if (temp.exists()) temp.delete();
            return absolutePath.substring(0, absolutePath.lastIndexOf(File.separator));
        }
        catch (Exception ignored){

        }
        return "";

    }
*/
}
