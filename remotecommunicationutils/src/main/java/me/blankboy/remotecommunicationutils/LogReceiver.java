package me.blankboy.remotecommunicationutils;

public interface LogReceiver {
    void onLogReceived(LogData log, Logger sender);
    void onLogRefresh(LogData log, int line, Logger sender);
}
