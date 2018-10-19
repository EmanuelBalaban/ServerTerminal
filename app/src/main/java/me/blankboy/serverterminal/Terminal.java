package me.blankboy.serverterminal;

import android.app.Activity;
import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Objects;

import me.blankboy.remotecommunicationutils.*;

import static java.lang.Thread.sleep;

public class Terminal extends AppCompatActivity implements LogReceiver, ChannelListener {

    Logger Console = new Logger();
    @Override
    public void onLogReceived(LogData logData) {
        final LogData log = logData;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView debug = findViewById(R.id.debugText);
                debug.append("\n" + log.toString("[{type}] {text}", ""));
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);
        Console.addListener(this);
        Console.Debug = true;

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    while (true){
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Runtime runtime = Runtime.getRuntime();
                                long usedMemInMB = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
                                long maxHeapSizeInMB = runtime.maxMemory() / 1048576L;
                                long availHeapSizeInMB = maxHeapSizeInMB - usedMemInMB;
                                TextView heapStatus = findViewById(R.id.textView);
                                String heapMessage = usedMemInMB + "MB in use and " + availHeapSizeInMB + "MB available of " + maxHeapSizeInMB + "MB in total.";
                                heapStatus.setText(heapMessage);
                            }
                        });
                        sleep(500);
                    }
                }
                catch (Exception ignored){

                }
            }
        });

        t.start();
    }



    public void onClick(View view){
        hideKeyboardFrom(this, view);
        switch (view.getId()){
            case R.id.connectButton:
                if (Variables.aChannel != null) {
                    try {
                        Variables.aChannel.close();
                    } catch (Exception ignored) {

                    }
                }
                EditText ip = findViewById(R.id.ipEditText);
                EditText port = findViewById(R.id.portEditText);
                final SocketAddress endPoint = new InetSocketAddress(ip.getText().toString(), Integer.valueOf(port.getText().toString()));

                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Socket so = new Socket();
                            so.connect(endPoint);
                            Console.Log("Connected!");
                            Variables.aChannel = new AdvancedChannel(new Connection(0, so, Console, true));
                        }
                        catch (Exception ex){
                            Console.Log(ex.getMessage());
                        }
                    }
                });
                t.start();
                try {
                    t.join();
                } catch (InterruptedException e) {
                    Console.Log(e.getMessage());
                }
                if (Variables.aChannel != null)
                    Variables.aChannel.addListener(this);
                break;
            case R.id.connectQRButton:
                break;
            case R.id.sendButton:
                EditText mess = findViewById(R.id.messageEditText);
                if (Variables.aChannel == null) Console.Log("aChannel is null!");
                else if (!Variables.aChannel.isStillConnected()) Console.Log("isStillConnected() returned false!");
                //if (Variables.aChannel != null && Variables.aChannel.isStillConnected())
                {
                    Variables.aChannel.sendMessage(mess.getText().toString(), (byte) 3);
                    Console.Log("You: " + mess.getText().toString());
                } //else Console.Log("You need to connect to a server first!");
                break;
            case R.id.sendFile:
                break;
        }
    }

    @Override
    public void onDataReceived(DataPackage data, Chunk chunk, AdvancedChannel sender) {
        if (chunk.Code == 3){
            try {
                String message = new String(chunk.readAllBytes());
                Console.Log("Server ( " + chunk.Latency() + "ms ): " + message);
                //Console.Log(String.valueOf(chunk.Sent));
                //Console.Log(String.valueOf(System.currentTimeMillis()));
            } catch (IOException e) {
                Console.Log(e.toString());
            }
        }
    }

    @Override
    public void onExceptionOccurred(Exception ex, AdvancedChannel sender) {
        // Nothing here, exception are directly logged to Console as its passed by Connection to aChannel.
    }

    public static void hideKeyboardFrom(Context context, View view) {
        ((InputMethodManager) Objects.requireNonNull(context.getSystemService(Activity.INPUT_METHOD_SERVICE))).hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
}
