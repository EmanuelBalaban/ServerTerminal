package me.blankboy.remotecommunicationutils;

import java.util.HashMap;
import java.util.Map;

// Just a list of use full stream processor codes.
enum StreamCodes {
    // Code for confirmation.
    Confirm((byte)-1),

    // Code for append with confirmation request.
    Append((byte)1),

    // Code for append without confirmation.
    QuickAppend((byte)2);

    private byte value;
    private static Map map = new HashMap<>();

    StreamCodes(byte value) { this.value = value; }
    public byte getValue() { return value; }

    static {
        for (StreamCodes code : StreamCodes.values()) {
            map.put(code.value, code);
        }
    }

    public static StreamCodes valueOf(byte code) {
        return (StreamCodes) map.get(code);
    }
}
