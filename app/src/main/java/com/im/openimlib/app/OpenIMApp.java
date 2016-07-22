package com.im.openimlib.app;


import android.app.Activity;

import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.litepal.LitePalApplication;

import java.util.ArrayList;

public class OpenIMApp extends LitePalApplication {
    /**
     * 在登录注册界面创建的连接对象 登录界面赋值
     */
    public static XMPPTCPConnection connection;
//    /**
//     * 应用是否在前台
//     */
//    public static boolean isActive;
    /**
     * 登录后存储用户名
     */
    public static String username;
    /**
     * 正在聊天的好友的用户名
     */
    public static String friendName;

    /**
     * 头像地址
     */
    public static String avatarUrl;
    private static ArrayList<Activity> activities = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
    }

    /**
     * 存放Activity到list中
     */
    public static void addActivity(Activity activity) {
        activities.add(activity);
    }

    public static void clearActivity() {
        for (Activity activity : activities) {
            activity.finish();
        }
//        android.os.Process.killProcess(android.os.Process.myPid());
    }
}

