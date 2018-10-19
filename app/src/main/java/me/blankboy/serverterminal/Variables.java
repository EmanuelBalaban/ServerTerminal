package me.blankboy.serverterminal;

import java.net.Socket;

import me.blankboy.remotecommunicationutils.AdvancedChannel;

public final class Variables {
    public static volatile AdvancedChannel aChannel = null;
    public static volatile Socket aSocket = new Socket();
}
