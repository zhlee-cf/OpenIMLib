package com.im.openimlib.service;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Toast;

import com.im.openimlib.Utils.MyBase64Utils;
import com.im.openimlib.Utils.MyConstance;
import com.im.openimlib.Utils.MyLog;
import com.im.openimlib.Utils.MyNetUtils;
import com.im.openimlib.Utils.MyVCardUtils;
import com.im.openimlib.Utils.ThreadUtil;
import com.im.openimlib.Utils.XMPPConnectionUtils;
import com.im.openimlib.app.OpenIMApp;
import com.im.openimlib.bean.VCardBean;
import com.im.openimlib.dao.OpenIMDao;
import com.im.openimlib.receiver.MyChatMessageListener;
import com.im.openimlib.receiver.ScreenListener;
import com.im.openimlib.receiver.TickAlarmReceiver;

import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManager;
import org.jivesoftware.smack.chat.ChatManagerListener;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.offline.OfflineMessageHeader;
import org.jivesoftware.smackx.offline.OfflineMessageManager;
import org.jivesoftware.smackx.offline.packet.OfflineMessageRequest;
import org.jivesoftware.smackx.ping.PingManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 应用主服务进程
 *
 * @author Administrator
 */
public class IMService extends Service {

    private static final int LOGIN_FIRST = 1000;
    private static final int LOGIN_SECOND = 2000;
    private static final int LOGIN_FAIL = 3000;
    private String username;
    private static IMService mIMService;

    private NotificationManager notificationManager;
    private XMPPTCPConnection connection;
    private ChatManagerListener myChatManagerListener;
    private ChatManager cm;

    private String password;
    private ConnectionListener mConnectionListener;
    private OpenIMDao openIMDao;

    private boolean loginState = true;
    private BroadcastReceiver mAppForegroundReceiver;
    private boolean loginFirst;
    private AlertDialog dialog;
    private BroadcastReceiver mHomeKeyDownReceiver;
    private BroadcastReceiver mActOnResumeListener;
    private ScreenListener screenListener;
    private BroadcastReceiver mInitOfflineMessageListener;
    private PendingIntent tickPendIntent;

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        MyLog.showLog("IMService OnCreate");

        loginFirst = true;
        // 初始化服务里需要使用的对象
        initObject();
        // 初始化数据
        initData();
        // 开启前台进程  不在状态栏添加图标
        startForeground(0, null);
        // 开启计时器 每5分钟唤醒一次服务
        setTickAlarm();
//        // 注册应用是否在前台监听
//        registerAppForegroundListener();
        // 注册act可见监听
        registerActOnResumeListener();
        if (MyNetUtils.isNetworkConnected(mIMService)) {
            initLoginState();
        } else {
            //注册连接状态监听
            registerConnectionListener();
        }
        // 添加系统发出的取消Dialog的广播 用于处理Home键
        registerHomeKeyDownListener();
        // 注册屏幕状态监听
        registerScreenListener();
        // 监听应用可见时，若ping的通，则会收到初始化离线消息的广播
        registerInitOfflineMessageListener();
    }

    /**
     * 监听应用可见时，若ping的通，则会收到初始化离线消息的广播
     */
    private void registerInitOfflineMessageListener() {
        mInitOfflineMessageListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                initOfflineMessages();
            }
        };
        IntentFilter filter = new IntentFilter(MyConstance.INIT_OFFLINE_MESSAGE_ACTION);
        registerReceiver(mInitOfflineMessageListener, filter);
    }

    /**
     * 注册屏幕状态监听
     */
    private void registerScreenListener() {
        screenListener = new ScreenListener(this);
        screenListener.begin(new ScreenListener.ScreenStateListener() {
            @Override
            public void onUserPresent() {
            }

            @Override
            public void onScreenOn() {
                MyLog.showLog("亮屏");
//                if (!OpenIMApp.isActive) {
                    ThreadUtil.runOnBackThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!loginFirst) {  // 创建服务 也就是正常首次登录时 不ping
                                if (!isServerReachable()) {
                                    loginServer();
                                    initOfflineMessages();
//                                    handler.post(new Runnable() {
//                                        @Override
//                                        public void run() {
//                                            Toast.makeText(mIMService, "亮屏登录成功", Toast.LENGTH_SHORT).show();
//                                        }
//                                    });
                                } else {
                                    initOfflineMessages();
                                }
                            }
                        }
                    });
//                }
            }

            @Override
            public void onScreenOff() {
                MyLog.showLog("锁屏");
//                OpenIMApp.isActive = false;
            }
        });
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
        } catch (NotConnectedException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 重新初始化connection对象并登录
     */
    private void loginServer() {
        // 移除跟connection有关的监听并断开链接
        removeListener();
        connection.disconnect();
        // 重新初始化connection对象并连接登录
        XMPPConnectionUtils.initXMPPConnection(mIMService);
        connection = OpenIMApp.connection;
        MyLog.showLog("loginServer===============connect" + connection.isConnected());
        try {
            connection.connect();
            MyLog.showLog("loginServer===============auth" + connection.isAuthenticated());
            connection.login(username, password);
            handler.sendEmptyMessage(LOGIN_FIRST);
        } catch (SmackException | IOException | XMPPException e) {
//            handler.post(new Runnable() {
//                @Override
//                public void run() {
//                    Toast.makeText(mIMService, "loginServer------" + e.getMessage(), Toast.LENGTH_SHORT).show();
//                }
//            });
            e.printStackTrace();
        }
    }

    /**
     * 界面可见监听
     */
    private void registerActOnResumeListener() {

        mActOnResumeListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (MyNetUtils.isNetworkConnected(mIMService)) {
                    if (!loginState) {
                        initLoginState();
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter(MyConstance.ACT_ONRESUME_ACTION);
        registerReceiver(mActOnResumeListener, filter);
    }

    /**
     * 监听的是 系统发出的取消Dialog的广播  反正点击home键会发出 当应用在前台时，锁屏键也会发出
     * 通过发出这个广播的reason可以判断这个广播是否是是在home键按下时发出的
     */
    private void registerHomeKeyDownListener() {
        final String SYSTEM_DIALOG_REASON_KEY = "reason";
        final String SYSTEM_DIALOG_REASON_HOME_KEY = "homekey";
        mHomeKeyDownReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                    String reason = intent.getStringExtra(SYSTEM_DIALOG_REASON_KEY);
                    if (reason != null && reason.equals(SYSTEM_DIALOG_REASON_HOME_KEY)) {
                        MyLog.showLog("捕获到home键");
                        dismissDialog();
                    }
                }
            }
        };
        registerReceiver(mHomeKeyDownReceiver, new IntentFilter(
                Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
    }

    /**
     * 判断是否是登录状态
     * 若已登录 则不做操作
     * 若未连接 则连接并登录
     * 若conn对象为空 则初始化对象并连接登录
     */
    private synchronized void initLoginState() {
        // 判断连接是否为空 如果为空则重新登录
        if (connection == null) {
            MyLog.showLog("1");
            XMPPConnectionUtils.initXMPPConnection(mIMService);
            connection = OpenIMApp.connection;
            reLogin();
        } else if (!connection.isConnected()) {
            MyLog.showLog("2---auth" + connection.isAuthenticated());
            reLogin();
        } else if (!connection.isAuthenticated()) {
            MyLog.showLog("3");
            try {
                connection.sendStanza(new Presence(Presence.Type.unavailable));
            } catch (NotConnectedException e) {
                e.printStackTrace();
            }
            reLogin();
        } else {
            if (loginFirst) {
                MyLog.showLog("4");
                handler.sendEmptyMessage(LOGIN_FIRST);
            } else {
                MyLog.showLog("5");
                handler.sendEmptyMessage(LOGIN_SECOND);
            }
        }
    }

    /**
     * 初始化服务中需要使用的对象
     */
    private void initObject() {
        mIMService = this;
        openIMDao = OpenIMDao.getInstance(mIMService);
//        sp = getSharedPreferences(MyConstance.SP_NAME, 0);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (OpenIMApp.connection == null) {
            XMPPConnectionUtils.initXMPPConnection(mIMService);
            MyLog.showLog("重新初始化连接");
        }
        connection = OpenIMApp.connection;
    }

    /**
     * 初始化 获取用户名和密码
     */
    private void initData() {

    }

    /**
     * 注册连接状态监听
     */
    private void registerConnectionListener() {
        if (mConnectionListener == null && connection != null) {  //只添加一个连接状态监听
            mConnectionListener = new ConnectionListener() {
                @Override
                public void connected(XMPPConnection connection) {
                    MyLog.showLog("-------连接成功--------");
                }

                @Override
                public void authenticated(XMPPConnection connection, boolean resumed) {
                    loginState = true;
                    MyLog.showLog("-------登录成功--------");
                    // 获取离线消息
                    initOfflineMessages();
                    OpenIMApp.connection = (XMPPTCPConnection) connection;

                    if (dialog != null && dialog.isShowing()) {
                        dialog.dismiss();
                    }
                }

                @Override
                public void connectionClosed() {
                    MyLog.showLog("连接被关闭");
                    loginState = false;

                }

                @Override
                public void connectionClosedOnError(final Exception e) {
                    MyLog.showLog("因为错误，连接被关闭");
                    loginState = false;
//                    handler.post(new Runnable() {
//                        @Override
//                        public void run() {
//                            Toast.makeText(mIMService, e.getMessage(), Toast.LENGTH_LONG).show();
//                        }
//                    });
                    MyLog.showLog("关闭异常信息::" + e.toString());
                    // 登录冲突
                    if (e.getMessage().contains("conflict")) {
                        showDialog();
                    } else {
                        final Timer timer = new Timer();
                        timer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                if (connection != null && MyNetUtils.isNetworkConnected(mIMService) && !loginState) {
                                    try {
                                        if (!connection.isConnected()) {
                                            connection.connect();
                                        }
                                        if (!connection.isAuthenticated()) {
                                            connection.login(username, password);
                                            handler.sendEmptyMessage(LOGIN_FIRST);
                                        } else {
                                            boolean isReachable = isServerReachable();
                                            if (isReachable) {
                                                handler.sendEmptyMessage(LOGIN_SECOND);
                                                MyLog.showLog("已经登录过了" + connection.isConnected());
                                            } else {
                                                // TODO
                                                loginServer();
//                                                handler.post(new Runnable() {
//                                                    @Override
//                                                    public void run() {
//                                                        Toast.makeText(mIMService, "异常中登录", Toast.LENGTH_SHORT).show();
//                                                    }
//                                                });
                                            }
                                        }
                                        timer.cancel();
                                    } catch (SmackException | IOException | XMPPException e1) {

                                        if (e.getMessage().contains("Client is already logged in")) {

//                                            handler.post(new Runnable() {
//                                                @Override
//                                                public void run() {
//                                                    Toast.makeText(mIMService, "异常登录成功----已经登录过了", Toast.LENGTH_SHORT).show();
//                                                }
//                                            });

                                            handler.sendEmptyMessage(LOGIN_FIRST);
                                        }
                                        e1.printStackTrace();
                                    }
                                }
                            }
                        }, 2000, 10000);
                    }
                }

                @Override
                public void reconnectionSuccessful() {
                    MyLog.showLog("重新连接成功");
                }

                @Override
                public void reconnectingIn(int seconds) {
                    MyLog.showLog("正在重新连接");
                }

                @Override
                public void reconnectionFailed(Exception e) {
                    MyLog.showLog("重新连接失败");
                }
            };
            connection.addConnectionListener(mConnectionListener);
        }
    }

    /**
     * 方法  判断连接是否为空 为空则重新登录
     */
    private synchronized void reLogin() {
        ThreadUtil.runOnBackThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!connection.isConnected()) {
                        connection.connect();
                    }
                    connection.setPacketReplyTimeout(60 * 1000);
                    if (connection.isAuthenticated()) {  //当应用断网时，connection不为null 并且这个conn已经登录过了
                        boolean isReachable = isServerReachable();
                        if (isReachable) {
                            handler.sendEmptyMessage(LOGIN_SECOND);
                            MyLog.showLog("已经登录过了" + connection.isConnected());
                        } else {
                            // TODO
                            loginServer();
                        }
                    } else {
                        connection.login(username, password);
                        MyLog.showLog("服务中重新登录");
                        handler.sendEmptyMessage(LOGIN_FIRST);
                    }
                    OpenIMApp.username = username;
                } catch (SmackException | IOException | XMPPException e) {
                    handler.sendEmptyMessage(LOGIN_FAIL);
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 开个计时器类似的  唤醒服务
     */
    protected void setTickAlarm() {
        AlarmManager alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, TickAlarmReceiver.class);
        int requestCode = 0;
        tickPendIntent = PendingIntent.getBroadcast(this,
                requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        //小米2s的MIUI操作系统，目前最短广播间隔为5分钟，少于5分钟的alarm会等到5分钟再触发
        long triggerAtTime = System.currentTimeMillis();
        MyLog.showLog("triggerAtTime::" + triggerAtTime);
        int interval = 180 * 1000;
        alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP, triggerAtTime, interval, tickPendIntent);
    }

    /**
     * 取消计时器
     */
    protected void cancelTickAlarm() {
        AlarmManager alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmMgr.cancel(tickPendIntent);
    }

    @Override
    /**
     * START_STICKY  提高服务的优先级的 但是貌似效果不明显
     * 在运行onStartCommand后service进程被kill后，那将保留在开始状态，但是不保留那些传入的intent。
     * 不久后service就会再次尝试重新创建，因为保留在开始状态，在创建service后将保证调用onstartCommand。
     * 如果没有传递任何开始命令给service，那将获取到null的intent。
     */
    public int onStartCommand(Intent intent, int flags, int startId) {
        username = intent.getStringExtra("username");
        if (OpenIMApp.username == null) {
            OpenIMApp.username = username;
        }
        password = MyBase64Utils.decode(intent.getStringExtra("password"));
        return START_REDELIVER_INTENT;
    }

    /**
     * 获取离线消息管理者 必须在用户登录之后才可以执行
     * 
     */
    private void initOfflineMessages() {
        ThreadUtil.runOnBackThread(new Runnable() {
            @Override
            public void run() {
                if (connection != null && connection.isConnected() && connection.isAuthenticated()) {
                    OfflineMessageManager offlineMessageManager = new OfflineMessageManager(connection);
                    try {
                        boolean isSupport = offlineMessageManager.supportsFlexibleRetrieval();
                        if (isSupport) {
                            int messageCount = offlineMessageManager.getMessageCount();
                            MyLog.showLog("离线消息数::" + messageCount);
                            ArrayList<String> nodes = new ArrayList<>();
                            while (messageCount > 5) {
                                nodes.clear();
                                List<OfflineMessageHeader> headers = offlineMessageManager.getHeaders();
                                for (int i = 0; i < 5; i++) {
                                    nodes.add(headers.get(i).getStamp());
                                }
                                /**
                                 * 自定义方法 根据nodes获取服务器指定的离线消息(Smack提供的消息太耗时了)
                                 */
                                getOfflineMessageByNodes(nodes);
                                /**
                                 * 自定义方法 根据nodes删除服务器指定离线消息
                                 */
                                deleteOfflineMessagesByNodes(nodes);
                                messageCount = offlineMessageManager.getMessageCount();
                            }
                            /**
                             * 获取离线消息
                             */
                            offlineMessageManager.getMessages();
                            /**
                             * 删除服务器端的离线消息
                             */
                            deleteOfflineMessages();
                            /**
                             * 将状态设置成在线  连接时不告诉服务器状态
                             */
                            Presence presence = new Presence(Presence.Type.available);
                            connection.sendStanza(presence);
                        }
                    } catch (SmackException.NoResponseException | XMPPException.XMPPErrorException | NotConnectedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    /**
     * 自定义方法 根据nodes获取服务器指定的离线消息(Smack提供的消息太耗时了)
     *
     * @param nodes
     */
    private void getOfflineMessageByNodes(ArrayList<String> nodes) {
        OfflineMessageRequest request = new OfflineMessageRequest();
        Iterator messageFilter = nodes.iterator();
        while (messageFilter.hasNext()) {
            String messageCollector = (String) messageFilter.next();
            OfflineMessageRequest.Item message = new OfflineMessageRequest.Item(messageCollector);
            message.setAction("view");
            request.addItem(message);
        }
        try {
            connection.createPacketCollectorAndSend(request).nextResultOrThrow();
        } catch (SmackException.NoResponseException | XMPPException.XMPPErrorException | NotConnectedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 删除服务器端所有的离线消息
     *
     * @throws SmackException.NoResponseException
     * @throws XMPPException.XMPPErrorException
     * @throws NotConnectedException
     */
    private void deleteOfflineMessages() throws SmackException.NoResponseException, XMPPException.XMPPErrorException, NotConnectedException {
        OfflineMessageRequest request = new OfflineMessageRequest();
        request.setPurge(true);
        request.setType(IQ.Type.set);
        connection.createPacketCollectorAndSend(request).nextResultOrThrow();
    }

    /**
     * 通过nodes删除服务器端指定的离线消息
     *
     * @param nodes
     * @throws SmackException.NoResponseException
     * @throws XMPPException.XMPPErrorException
     * @throws NotConnectedException
     */
    private void deleteOfflineMessagesByNodes(ArrayList<String> nodes) throws SmackException.NoResponseException, XMPPException.XMPPErrorException, NotConnectedException {
        OfflineMessageRequest request = new OfflineMessageRequest();
        Iterator iterator = nodes.iterator();
        while (iterator.hasNext()) {
            String node = (String) iterator.next();
            OfflineMessageRequest.Item item = new OfflineMessageRequest.Item(node);
            item.setAction("remove");
            request.addItem(item);
            request.setType(IQ.Type.set);
        }
        connection.createPacketCollectorAndSend(request).nextResultOrThrow();
    }

    /**
     * 方法 把自身返回 方便外部调用
     *
     * @return 服务本身对象
     */
    public static IMService getInstance() {
        return mIMService;
    }


    /**
     * 获取好友 及自己的vCard信息并存储到数据库
     */
    private void initSelfInfo() {
        if (connection != null) {
            ThreadUtil.runOnBackThread(new Runnable() {
                @Override
                public void run() {
                    // 登录后查询自己的VCard信息
                    VCardBean userVCard = MyVCardUtils.queryVCard(null);
                    if (userVCard != null) {
                        OpenIMApp.avatarUrl = userVCard.getAvatar();
                        userVCard.setJid(username + "@" + MyConstance.SERVICE_HOST);
                        openIMDao.updateSingleVCard(userVCard);
                    }
                }
            });
        }
    }

    /**
     * 方法 每隔30秒 ping一下服务器
     */
    private void initPingConnection() {
        PingManager pingManager = PingManager.getInstanceFor(connection);
        pingManager.setPingInterval(30);
        try {
            pingManager.pingMyServer(false, 10 * 1000);
        } catch (NotConnectedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 注册消息接收监听
     */
    private void registerMessageListener() {
        if (connection != null && connection.isAuthenticated()) {
            cm = ChatManager.getInstanceFor(connection);
            myChatManagerListener = new ChatManagerListener() {
                @Override
                public void chatCreated(Chat chat, boolean createdLocally) {
                    // 通过会话对象 注册一个消息接收监听
                    chat.addMessageListener(new MyChatMessageListener(mIMService, notificationManager));
                }
            };
            cm.addChatListener(myChatManagerListener);
        }
    }

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(android.os.Message msg) {
            super.handleMessage(msg);
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
            switch (msg.what) {
                case LOGIN_FIRST:
                    loginState = true;
                    loginFirst = false;

                    sendBroadcast(new Intent(MyConstance.NEW_CONNECTION_ACTION));

                    //注册连接状态监听
                    registerConnectionListener();
                    // 消息接收监听
                    registerMessageListener();
                    // 初始化离线消息
                    initOfflineMessages();
                    // ping服务器
                    initPingConnection();
                    //获取好友 及自己的vCard信息并存储到数据库
                    initSelfInfo();
                    break;
                case LOGIN_SECOND:
                    loginState = true;
                    loginFirst = false;
                    // 初始化离线消息
                    initOfflineMessages();
                    break;
                case LOGIN_FAIL:
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mIMService, "登录失败", Toast.LENGTH_LONG).show();
                        }
                    });
                    break;
            }
        }
    };

    /**
     * 方法 移除各种监听
     */
    private void removeListener() {
        if (cm != null && myChatManagerListener != null) { //移除单人消息监听
            cm.removeChatListener(myChatManagerListener);
            myChatManagerListener = null;
        }
        if (connection != null && mConnectionListener != null) {  //移除连接状态监听
            connection.removeConnectionListener(mConnectionListener);
            mConnectionListener = null;
        }
    }

    @Override
    public void onDestroy() {
        //移除各种监听
        removeListener();

        if (mAppForegroundReceiver != null) {  // 移除APP前台监听
            unregisterReceiver(mAppForegroundReceiver);
            mAppForegroundReceiver = null;
        }

        if (mInitOfflineMessageListener != null) {  // 移除离线消息初始化监听
            unregisterReceiver(mInitOfflineMessageListener);
            mInitOfflineMessageListener = null;
        }

        if (mActOnResumeListener != null) {  // 移除界面可见监听
            unregisterReceiver(mActOnResumeListener);
            mActOnResumeListener = null;
        }

        if (mHomeKeyDownReceiver != null) {   // 移除对Home键的监听
            unregisterReceiver(mHomeKeyDownReceiver);
            mHomeKeyDownReceiver = null;
        }

        if (screenListener != null) {  // 移除屏幕状态监听
            screenListener.unregisterListener();
            screenListener = null;
        }
        // 服务销毁时 断开连接
        if (connection != null && connection.isConnected()) {
            connection.disconnect();
        }

        cancelTickAlarm();
        super.onDestroy();
        MyLog.showLog("服务被销毁");
    }

    /**
     * 服务中弹Dialog
     */
    private void showDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mIMService);
        builder.setTitle("提示");
        builder.setMessage("您已被挤掉线");
        builder.setNegativeButton("退出应用", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                stopSelf();
                OpenIMApp.clearActivity();
//                // 被挤掉线 如果选择退出应用 则清空密码
//                sp.edit().putString("password", "").apply();
            }
        });
        builder.setPositiveButton("重新登录", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (connection != null && mConnectionListener != null) {  //移除连接状态监听
                    connection.removeConnectionListener(mConnectionListener);
                    mConnectionListener = null;
                }
                XMPPConnectionUtils.initXMPPConnection(mIMService);
                connection = OpenIMApp.connection;
                try {
                    if (!connection.isConnected()) {
                        connection.connect();
                    }
                    connection.login(username, password);
                    handler.sendEmptyMessage(LOGIN_FIRST);
                } catch (XMPPException | SmackException | IOException e) {
                    e.printStackTrace();
                }
            }
        });
        builder.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if (KeyEvent.KEYCODE_BACK == keyCode) {
                    stopSelf();
                    OpenIMApp.clearActivity();
                }
                return false;
            }
        });
        Looper.prepare();
        dialog = builder.create();
        //在dialog  show方法之前添加如下代码，表示该dialog是一个系统的dialog**
        dialog.getWindow().setType((WindowManager.LayoutParams.TYPE_SYSTEM_ALERT));
        dialog.show();
        Looper.loop();
    }

    /**
     * 隐藏对话框
     */
    private void dismissDialog() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
            stopSelf();
            OpenIMApp.clearActivity();
        }
    }
}
