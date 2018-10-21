package me.blankboy.remotecommunicationutils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class Logger {
    // interface stuff
    private List<LogReceiver> Listeners = new ArrayList<>();
    public void addListener(LogReceiver receiver){
        if (!Listeners.contains(receiver))
            Listeners.add(receiver);
    }
    private void broadcast(LogData log){
        for (LogReceiver l : Listeners)
            l.onLogReceived(log, this);
    }
    private void refresh(LogData log, int line){
        for (LogReceiver l : Listeners)
            l.onLogRefresh(log, line, this);
    }

    public boolean Debug = false;

    public String LineFormat = "{date} - {type} - {text}";
    public String DateFormat = "HH:mm:ss";

    public List<LogData> Logs = new ArrayList<>();

    public int Log(String message){
        return Log(message, TypesOLog.INFO);
    }
    public int Log(String message, int lineID){
        return Log(message, TypesOLog.INFO, lineID);
    }
    public int Log(String message, TypesOLog messType){
        return Log(message, messType, -1);
    }
    public int Log(String message, TypesOLog messType, int lineID)
    {
        LogData mess = new LogData();

        mess.Text = message;
        mess.Type = messType;
        mess.Time = new Date(System.currentTimeMillis());

        if (0 <= lineID && lineID < Logs.size()) {
            Logs.set(lineID, mess);
            if (messType != TypesOLog.DEBUG || Debug) refresh(mess, lineID);
            return lineID;
        }
        else {
            Logs.add(mess);
            if (messType != TypesOLog.DEBUG || Debug) broadcast(mess);
            return Logs.size() - 1;
        }
        /*
        if (messType != TypesOLog.DEBUG || Debug){
            broadcast(mess);
        }
        return lineID;
        */
    }

    @Override
    public String toString()
    {
        return GetConsole(LineFormat, DateFormat);
    }

    public String GetConsole(String lineFormat, String dateFormat){
        return GetConsole(lineFormat, dateFormat, false);
    }
    public String GetConsole(String lineFormat, String dateFormat, boolean saveFormats)
    {
        if (saveFormats)
        {
            LineFormat = lineFormat;
            DateFormat = dateFormat;
        }
        String result = "";
        Collections.sort(Logs);
        for (LogData l : Logs) result += l.toString(lineFormat, dateFormat) + "\n";
        return result;
    }
}
