package com.im.openimlib.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.im.openimlib.service.IMService;
import com.im.openimlib.Utils.MyLog;
import com.im.openimlib.Utils.MyUtils;


/**
 * 收到广播时，判断IMService是否在运行中，若不在则启动服务
 */
public class TickAlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        MyLog.showLog("OpenIM收到Alarm广播");
        boolean isIMServiceRunning = MyUtils.isServiceRunning(context, "IMService");
        if (!isIMServiceRunning) {
            context.startService(new Intent(context, IMService.class));
        }
    }
}
