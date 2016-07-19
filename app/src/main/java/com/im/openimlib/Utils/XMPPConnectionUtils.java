package com.im.openimlib.Utils;

import android.content.Context;
import android.os.Environment;

import com.im.openimlib.app.MyApp;
import com.im.openimlib.dao.OpenIMDao;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.Roster.SubscriptionMode;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.receipts.DeliveryReceipt;
import org.jivesoftware.smackx.receipts.DeliveryReceiptManager;
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest;
import org.jivesoftware.smackx.receipts.ReceiptReceivedListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

public class XMPPConnectionUtils {

    private static String sendLogPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/OpenIM/send_log.txt";
    private static String receiveLogPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/OpenIM/receive_log.txt";

    private static File sendFile = new File(sendLogPath);
    private static File receiveFile = new File(receiveLogPath);
    private static OpenIMDao openIMDao;

    /**
     * 初始化连接
     */
    public static void initXMPPConnection(Context ctx) {

        openIMDao = OpenIMDao.getInstance(ctx);

        final XMPPTCPConnectionConfiguration.Builder configBuilder = XMPPTCPConnectionConfiguration.builder();

        // 设置主机IP地址ַ
        configBuilder.setHost(MyConstance.SERVICE_HOST);
        configBuilder.setPort(5222);
        configBuilder.setServiceName(MyConstance.SERVICE_HOST);
        configBuilder.setConnectTimeout(30 * 1000);
        configBuilder.setSecurityMode(ConnectionConfiguration.SecurityMode.disabled);
        configBuilder.setSendPresence(false);


        // 设置手动同意好友请求
        Roster.setDefaultSubscriptionMode(SubscriptionMode.manual);

        // 获取连接对象
        final XMPPTCPConnection connection = new XMPPTCPConnection(configBuilder.build());

        System.setProperty("http.keepAlive", "false");

        // 消息回执
        ProviderManager.addExtensionProvider(DeliveryReceipt.ELEMENT, DeliveryReceipt.NAMESPACE, new DeliveryReceipt.Provider());
        ProviderManager.addExtensionProvider(DeliveryReceiptRequest.ELEMENT, DeliveryReceipt.NAMESPACE, new DeliveryReceiptRequest.Provider());
        DeliveryReceiptManager deliveryReceiptManager = DeliveryReceiptManager.getInstanceFor(connection);
        // 收到消息后 总是给回执
        deliveryReceiptManager.setAutoReceiptMode(DeliveryReceiptManager.AutoReceiptMode.always);
        // 自动添加要求回执的请求
        deliveryReceiptManager.autoAddDeliveryReceiptRequests();
        deliveryReceiptManager.addReceiptReceivedListener(new ReceiptReceivedListener() {
            @Override
            public void onReceiptReceived(String fromJid, String toJid, String receiptId, Stanza receipt) {
                MyLog.showLog("收到回执::" + receiptId);
                boolean isFromServer = isFromServer(fromJid);
                if (isFromServer) {
                    openIMDao.updateMessageReceipt(receiptId, "2");// 2表示已发送到服务器 1表示发送中  0表示收到消息
                } else {
                    openIMDao.updateMessageReceipt(receiptId, "3");// 3表示已送达 -1表示发送失败
                }
            }
        });
        // 设置使用流管理
        connection.setUseStreamManagement(true);
        connection.setUseStreamManagementResumption(true);

        // 设置不允许自动重连
        ReconnectionManager reconnectionManager = ReconnectionManager.getInstanceFor(connection);
        reconnectionManager.disableAutomaticReconnection();

        // 将连接对象变成全应用变量
        MyApp.connection = connection;

        /**
         * 监听创建连接后 发出的数据
         */
        connection.addPacketSendingListener(new StanzaListener() {

            @Override
            public void processPacket(Stanza packet) throws NotConnectedException {
                CharSequence xml = packet.toXML();
                if (!sendFile.getParentFile().exists()) {
                    sendFile.getParentFile().mkdirs();
                }
                try {
                    FileOutputStream fos = new FileOutputStream(sendFile, true);
                    fos.write((new Date() + "==============" + xml.toString()).getBytes());
                    fos.write("\n".getBytes());
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                MyLog.showLog("发出的流::" + xml.toString());
            }
        }, null);

        connection.addAsyncStanzaListener(new StanzaListener() {
            @Override
            public void processPacket(Stanza packet) throws NotConnectedException {
                CharSequence xml = packet.toXML();
                if (!receiveFile.getParentFile().exists()) {
                    receiveFile.getParentFile().mkdirs();
                }
                try {
                    FileOutputStream fos = new FileOutputStream(receiveFile, true);
                    fos.write((new Date() + "==============" + xml.toString()).getBytes());
                    fos.write("\n".getBytes());
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                MyLog.showLog("收到的流::" + xml.toString());
            }
        }, null);
    }

    /**
     * 判断回执是否来自服务器
     *
     * @param str
     * @return
     */
    private static boolean isFromServer(String str) {
        if (str != null && str.contains("@ack.openim.top")) {
            return true;
        }
        return false;
    }
}
