package com.im.openimlib.app;

import android.content.Context;
import android.content.Intent;

import com.im.openimlib.service.IMService;
import com.im.openimlib.Utils.MyBase64Utils;
import com.im.openimlib.Utils.MyUtils;

/**
 * Created by lzh12 on 2016/7/19.
 */
public class OpenIM {
    /**
     * 初始化OpenIM 启动核心服务
     * @param ctx 上下文对象
     * @param username  用户名
     * @param password  密码
     */
    public static void init(Context ctx, String username, String password) {
        if (!MyUtils.isServiceRunning(ctx, "com.im.openimlib.service.IMService")) {
            Intent service = new Intent(ctx, IMService.class);
            service.putExtra("username", username);
            service.putExtra("password", MyBase64Utils.encodeToString(password));
            ctx.startService(service);
        }
    }

    /**
     * 开启聊天界面
     * @param ctx 上下文对象
     * @param friendName  好友用户名 不含后缀
     * @param friendNick  聊天标题 一般为用户昵称
     */
    public static void startChat(Context ctx, String friendName, String friendNick) {
        Intent intent = new Intent();
        intent.setAction("com.openim.activity.chatactivity");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.putExtra("friendName", friendName);
        intent.putExtra("friendNick", friendNick);
        ctx.startActivity(intent);
    }

}
