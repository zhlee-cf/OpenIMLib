package com.im.openimlib.app;

import android.content.Context;
import android.content.Intent;

import com.im.openimlib.Utils.MyBase64Utils;
import com.im.openimlib.Utils.MyUtils;
import com.im.openimlib.service.IMService;

/**
 * Created by lzh12 on 2016/7/19.
 */
public class OpenIM {
    /**
     * 初始化OpenIM
     *
     * @param ctx
     */
    public static void init(Context ctx, String username, String password) {
        if (!MyUtils.isServiceRunning(ctx, "com.im.openimlib.service.IMService")) {
            Intent service = new Intent(ctx, IMService.class);
            service.putExtra("username", username);
            service.putExtra("password", MyBase64Utils.encodeToString(password));
            ctx.startService(service);
        }
    }
}
