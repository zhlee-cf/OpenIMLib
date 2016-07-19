package com.im.openimlib.activity;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v4.app.FragmentActivity;

import com.im.openimlib.Utils.MyConstance;
import com.im.openimlib.Utils.MyLog;
import com.im.openimlib.Utils.MyNetUtils;
import com.im.openimlib.Utils.MyUtils;
import com.im.openimlib.Utils.ThreadUtil;
import com.im.openimlib.app.MyApp;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.ping.PingManager;

import java.util.List;

public class BaseActivity extends FragmentActivity {
    private BaseActivity act;
    private boolean isFocus = true;
    private BroadcastReceiver newConnectReceiver;
    private XMPPTCPConnection connection;
    private PowerManager pm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        act = this;
        MyApp.addActivity(this);

        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

        connection = MyApp.connection;

        newConnectReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                doNewConnection();
            }
        };
        IntentFilter filter = new IntentFilter(MyConstance.NEW_CONNECTION_ACTION);
        registerReceiver(newConnectReceiver, filter);
    }

    /**
     * 接收创建新的connection对象的广播里的方法，子类如需使用需要重写
     */
    protected void doNewConnection() {
    }

    @Override
    protected void onResume() {
        connection = MyApp.connection;
        ThreadUtil.runOnBackThread(new Runnable() {
            @Override
            public void run() {
                if (connection != null) {
                    MyLog.showLog("应用可见_connected::" + connection.isConnected());
                    MyLog.showLog("应用可见_auth::" + connection.isAuthenticated());
                    MyLog.showLog("应用可见_socket_closed::" + connection.isSocketClosed());
                }
                if (connection == null || !connection.isConnected() || !connection.isAuthenticated()) {
                    if (MyNetUtils.isNetworkConnected(act) && isFocus) {
                        sendBroadcast(new Intent(MyConstance.ACT_ONRESUME_ACTION));
                    }
                } else {
                    boolean isReachable = isServerReachable();
                    MyLog.showLog("isReachable::" + isReachable);
                    MyUtils.showToast(act, "应用可见,ping结果::" + isReachable);
                    if (!isReachable) {
                        if (isFocus) {
                            sendBroadcast(new Intent(MyConstance.APP_FOREGROUND_ACTION));
                        }
                    }
                    if (!MyApp.isActive) {
                        MyApp.isActive = true;
                        MyLog.showLog("程序处于前台");
                        if (isReachable) {
                            sendBroadcast(new Intent(MyConstance.INIT_OFFLINE_MESSAGE_ACTION));
                        }
                    }
                }
            }
        });
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        isFocus = hasFocus;
    }

    @Override
    protected void onDestroy() {
        if (newConnectReceiver != null) {
            unregisterReceiver(newConnectReceiver);
        }
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        super.onStop();
        ThreadUtil.runOnBackThread(new Runnable() {
            @Override
            public void run() {
                if (!isAppOnForeground()) {
                    MyApp.isActive = false;
                    MyLog.showLog("程序处于后台");
                }
            }
        });
    }

    /**
     * 程序是否在前台运行
     *
     * @return true 前台  false 后台
     */
    public boolean isAppOnForeground() {
        ActivityManager activityManager = (ActivityManager) getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
        String packageName = getApplicationContext().getPackageName();

        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager
                .getRunningAppProcesses();
        if (appProcesses == null)
            return false;

        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.processName.equals(packageName)
                    && appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return true;
            }
        }
        return false;
    }

    /**
     * ping服务器，10秒未收到回执则认为已掉线
     *
     * @return
     */
    private boolean isServerReachable() {
        PingManager pingManager = PingManager.getInstanceFor(connection);
        try {
            return pingManager.pingMyServer(false, 10 * 1000);
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
            return false;
        }
    }
}