package com.im.openimlib.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.im.openimlib.Utils.MyBase64Utils;
import com.im.openimlib.Utils.MyConstance;
import com.im.openimlib.service.IMService;

/**
 * Created by lzh12 on 2016/7/19.
 */
public class OpenIM {
    /**
     * 初始化OpenIM
     * @param ctx
     */
    public static void init(Context ctx,String username,String password){
        SharedPreferences sp = ctx.getSharedPreferences(MyConstance.SP_NAME, 0);
        sp.edit().putString("username",username).apply();
        sp.edit().putString("password", MyBase64Utils.encodeToString(password)).apply();
        ctx.startService(new Intent(ctx, IMService.class));
    }
}
