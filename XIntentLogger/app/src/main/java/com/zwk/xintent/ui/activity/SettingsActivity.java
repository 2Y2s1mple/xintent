package com.zwk.xintent.ui.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;

import com.zwk.xintent.BuildConfig;
import com.zwk.xintent.R;
import com.zwk.xintent.utils.GloblePool;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";
    private static final int SEND_KEYS = 1;
    private static final int SEND_ACT_BACKUP = 2;
    private static final int SEND_ACT_DUMP = 3;
    public SharedPreferences preferenceSharedPreferences;
    public static Socket mClientSocket;
    public static PrintWriter mPrintWriter;
    public static Handler mHandler;
    public static HandlerThread mHandlerThread;


    private SharedPreferences.OnSharedPreferenceChangeListener mListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.startsWith("key_")) {
                Log.i(TAG, String.format("SP_key='%s' SP_val='%s'", key, sharedPreferences.getBoolean(key, false)));
            } else if (key.startsWith("SV_")) {
                Log.i(TAG, String.format("SP_key='%s' SP_val='%s'", key, sharedPreferences.getString(key, "")));
            }  else if (key.startsWith("act_")) {
                Log.i(TAG, String.format("act_key='%s' act_val='%s'", key, sharedPreferences.getString(key, "")));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
        }


        preferenceSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        mHandlerThread = new HandlerThread("PREF_SEND_THREAD");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case SEND_KEYS:
                        String configs = getConfigStr();
                        Log.d(TAG, "handleMessage: SEND_KEYS = " + configs);
                        mPrintWriter.println("KEYS" + configs);
                        break;
                    case SEND_ACT_BACKUP:
                        Log.d(TAG, "handleMessage: SEND_ACT_BACKUP");
                        mPrintWriter.println("BACK" + " ");  // must longer than 4
                        break;
                    case SEND_ACT_DUMP:
                        Log.d(TAG, "handleMessage: SEND_ACT_DUMP");
                        mPrintWriter.println("DUMP" + " ");
                    default:
                        break;
                }
                super.handleMessage(msg);
            }
        };
        new Thread() {
            @Override
            public void run() {
                connectSocketServer();
            }
        }.start();

    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            SwitchPreference key_startActivity = findPreference("key_startActivity");
            key_startActivity.setOnPreferenceChangeListener(this);
            SwitchPreference key_getContentProvider = findPreference("key_getContentProvider");
            key_getContentProvider.setOnPreferenceChangeListener(this);
            SwitchPreference key_sendBroadcast = findPreference("key_sendBroadcast");
            key_sendBroadcast.setOnPreferenceChangeListener(this);
            SwitchPreference key_registerReceiver = findPreference("key_registerReceiver");
            key_registerReceiver.setOnPreferenceChangeListener(this);
            SwitchPreference key_startService = findPreference("key_startService");
            key_startService.setOnPreferenceChangeListener(this);
            SwitchPreference key_bindService = findPreference("key_bindService");
            key_bindService.setOnPreferenceChangeListener(this);
            SwitchPreference key_startProcess = findPreference("key_startProcess");
            key_startProcess.setOnPreferenceChangeListener(this);
            CheckBoxPreference key_persistence = findPreference("key_persistence");
            key_persistence.setOnPreferenceChangeListener(this);
            SwitchPreference key_queryIntent = findPreference("key_queryIntent");
            key_queryIntent.setOnPreferenceChangeListener(this);

            Preference act_backupConfigs = findPreference("act_backupConfigs");
            act_backupConfigs.setOnPreferenceClickListener(this);
            Preference act_logDump = findPreference("act_logDump");
            act_logDump.setOnPreferenceClickListener(this);
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            SettingsActivity.mHandler.sendEmptyMessage(SEND_KEYS);
            return true;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            switch (preference.getKey()) {
                case "act_backupConfigs":
                    SettingsActivity.mHandler.sendEmptyMessage(SEND_ACT_BACKUP);
                    break;
                case "act_logDump":
                    SettingsActivity.mHandler.sendEmptyMessage(SEND_ACT_DUMP);
                    break;
                default:

            }
            return true;
        }
    }

    @Override
    protected void onResume() {
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).registerOnSharedPreferenceChangeListener(mListener);
        super.onResume();
    }

    @Override
    protected void onPause() {
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).unregisterOnSharedPreferenceChangeListener(mListener);
        super.onPause();
    }

    private void connectSocketServer() {
        Socket socket = null;
        int reConTime = 8;
        while (socket == null && reConTime-->0) {
            try {
                socket = new Socket(InetAddress.getLocalHost(), GloblePool.nsport);
                mClientSocket = socket;
                mPrintWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                Log.d(TAG, "connectSocketServer: success!");
                mPrintWriter.println("Send first hand shake");
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (ConnectException e) {
                final String msg = e.getMessage();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
            SystemClock.sleep(1000);
        }
    }

    @Override
    protected void onDestroy() {
        mPrintWriter.flush();
        mPrintWriter.close();
        if (mClientSocket != null) {
            try {
                mClientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        super.onDestroy();
    }

    public String getConfigStr() {
        StringBuilder sb = new StringBuilder();
        boolean key_sendBroadcast = preferenceSharedPreferences.getBoolean("key_sendBroadcast", true);
        boolean key_registerReceiver = preferenceSharedPreferences.getBoolean("key_registerReceiver", true);
        boolean key_getContentProvider = preferenceSharedPreferences.getBoolean("key_getContentProvider", true);
        boolean key_startProcess = preferenceSharedPreferences.getBoolean("key_startProcess", true);
        boolean key_bindService = preferenceSharedPreferences.getBoolean("key_bindService", true);
        boolean key_startActivity = preferenceSharedPreferences.getBoolean("key_startActivity", true);
        boolean key_startService = preferenceSharedPreferences.getBoolean("key_startService", true);
        boolean key_persistence = preferenceSharedPreferences.getBoolean("key_persistence", true);
        boolean key_queryIntent = preferenceSharedPreferences.getBoolean("key_queryIntent", true);

        sb.append("sA").append(":").append(key_startActivity).append(",");
        sb.append("gCP").append(":").append(key_getContentProvider).append(",");
        sb.append("sB").append(":").append(key_sendBroadcast).append(",");
        sb.append("rR").append(":").append(key_registerReceiver).append(",");
        sb.append("sS").append(":").append(key_startService).append(",");
        sb.append("bS").append(":").append(key_bindService).append(",");
        sb.append("sP").append(":").append(key_startProcess).append(",");
        sb.append("xlog").append(":").append(key_persistence).append(",");
        sb.append("qI").append(":").append(key_queryIntent).append(",");
        sb.deleteCharAt(sb.length()-1);

        return sb.toString();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_about:
                View aboutView = getLayoutInflater().inflate(R.layout.about_dialog, null);
                TextView textView = aboutView.findViewById(R.id.aboutView);
                AlertDialog.Builder aboutDialogBuilder = new AlertDialog.Builder(this);
                aboutDialogBuilder
                        .setTitle("XIntent v" + BuildConfig.VERSION_NAME)
                        .setIcon(R.mipmap.ic_intent)
                        .setView(aboutView)
                        .setPositiveButton("Report issue", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                intent.setData(Uri.parse("https://github.com/2Y2s1mple/xintent/issues"));
                                startActivity(intent);
                            }
                        });
                AlertDialog dialog = aboutDialogBuilder.create();
                dialog.show();
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setAllCaps(false);
                break;
            default:
        }
        return super.onOptionsItemSelected(item);
    }
}