package com.zwk.xintent.ipc;

import android.util.Log;

import com.zwk.xintent.utils.DumpUtils;
import com.zwk.xintent.utils.GloblePool;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.StringTokenizer;

public class TcpServer implements Runnable {
    private static final String TAG = "InjectTcpServer";
    private static final int TYPELEN = 4;
    private boolean isServiceDestroyed = false;
    @Override
    public void run() {
        Log.d(TAG, "run: ");
        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(GloblePool.nsport, 1);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        while (!isServiceDestroyed) {
            try {
                final Socket client = serverSocket.accept();
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            updateFromClient(client);
                        } catch (IOException e) {
                            e.printStackTrace();
                            return;
                        }
                    }
                }.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateFromClient(Socket client) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(client.getOutputStream())), true);
        out.println("Hello, I am InjectTcpServer.");
        while (!isServiceDestroyed) {
            String str = in.readLine();
            Log.i(TAG, "responseClient: " + str);

            if (str == null || str.equals("ByeBye")) {
                Log.i(TAG, "Client break connect.");
                break;
            }

            if (str.length() <= TYPELEN) continue;

            String type = str.substring(0, TYPELEN);
            Log.e(TAG, "type: " + type);

            if (type.equals("DUMP")) {
                DumpUtils.start();
            } else if (type.equals("BACK")) {
                GloblePool.LogConfig.tryBackupConfigs();
            } else if (type.equals("KEYS")) {
                String content = str.substring(TYPELEN);
                StringTokenizer st = new StringTokenizer(content, ",");
                while (st.hasMoreElements()) {
                    String one = st.nextToken();
                    int colon = one.indexOf(":");
                    if (colon > 0) {
                        String k = one.substring(0, colon);
                        String v = one.substring(colon + 1);
                        GloblePool.LogConfig.updateLogConfig(k, v);
                    }
                }
                Log.d(TAG, GloblePool.LogConfig.printStatus());

            }

            String msg = "Received Msg = " + str;
            out.println(msg);
        }
        out.close();
        in.close();
        client.close();
    }
}