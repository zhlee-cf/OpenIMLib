package com.im.openimlib.dao;

import android.content.Context;
import android.database.Cursor;

import com.im.openimlib.Utils.MyConstance;
import com.im.openimlib.bean.MessageBean;
import com.im.openimlib.bean.VCardBean;

import org.litepal.crud.DataSupport;
import org.litepal.tablemanager.Connector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 使用LitePal操作数据库
 * Created by Administrator on 2016/4/18.
 */
public class OpenIMDao {

    private static OpenIMDao instance;
    private Context ctx;

    private OpenIMDao(Context ctx) {
        this.ctx = ctx;
        // 创建数据库
        Connector.getDatabase();
    }

    /**
     * 单例模式
     *
     * @return openIMDao对象
     */
    public static synchronized OpenIMDao getInstance(Context ctx) {
        if (instance == null) {
            instance = new OpenIMDao(ctx);
        }
        return instance;
    }

    /**====================================== 操作VCard ==========================================*/

    /**
     * 更新单个的VCard信息
     *
     * @param vCardBean
     */
    public void updateSingleVCard(VCardBean vCardBean) {
        VCardBean singleVCard = findSingleVCard(vCardBean.getJid());
        if (singleVCard != null) {
            singleVCard.delete();
        }
        vCardBean.save();
        ctx.getContentResolver().notifyChange(MyConstance.URI_VCARD, null);
    }

    /**
     * 根据userJid查询相应的VCard信息
     *
     * @param userJid
     * @return
     */
    public VCardBean findSingleVCard(String userJid) {
        List<VCardBean> vCardBeans = DataSupport.where(DBColumns.JID + " = ?", userJid).find(VCardBean.class);
        if (vCardBeans != null && vCardBeans.size() > 0) {
            return vCardBeans.get(0);
        }
        return null;
    }

    /**====================================== 操作聊天信息 ==========================================*/


    /**
     * 查询最近50条消息中有没有这个stanzaId
     *
     * @param stanzaId
     * @return
     */
    public boolean isMessageExist(String stanzaId) {
        List<MessageBean> messageBeans = DataSupport.where(DBColumns.STANZA_ID + " = ?", stanzaId).limit(50).find(MessageBean.class);
        return messageBeans != null && messageBeans.size() > 0;
    }

    /**
     * 保存一条聊天信息到数据库
     *
     * @param messageBean
     */
    public void saveSingleMessage(MessageBean messageBean) {
        boolean messageExist = isMessageExist(messageBean.getStanzaId());
        if (!messageExist) {
            messageBean.save();
            // 发出通知，群组数据库发生变化了
            ctx.getContentResolver().notifyChange(MyConstance.URI_MSG, null);
        }
    }

    /**
     * 删除指定的聊天信息
     *
     * @param stanzaId
     */
    public void deleteSingleMessage(String stanzaId) {
        MessageBean singleMessage = findSingleMessage(stanzaId);
        if (singleMessage != null) {
            singleMessage.delete();
            // 发出通知，群组数据库发生变化了
            ctx.getContentResolver().notifyChange(MyConstance.URI_MSG, null);
        }
    }

    /**
     * 通过消息的stanzaId查找指定的消息
     *
     * @param stanzaId
     * @return
     */
    public MessageBean findSingleMessage(String stanzaId) {
        List<MessageBean> messageBeans = DataSupport.where(DBColumns.STANZA_ID + " = ?", stanzaId).find(MessageBean.class);
        if (messageBeans != null && messageBeans.size() > 0) {
            return messageBeans.get(0);
        }
        return null;
    }

    /**
     * 通过mark查询聊天信息  每次查询5条
     *
     * @param offset 查询偏移量
     * @param mark
     * @return
     */
    public List<MessageBean> findMessageByMark(String mark, int offset) {
        List<MessageBean> messageBeans = DataSupport.where(DBColumns.MARK + " = ?", mark).order(DBColumns.ID + " desc").limit(10).offset(offset).find(MessageBean.class);
        if (messageBeans != null) {
            Collections.reverse(messageBeans);
        }
        return messageBeans;
    }

    /**
     * 通过mark删除与指定人的聊天消息
     *
     * @param mark
     */
    public void deleteMessageByMark(String mark) {
        DataSupport.deleteAll(MessageBean.class, DBColumns.MARK + " = ?", mark);
        // 发出通知，群组数据库发生变化了
        ctx.getContentResolver().notifyChange(MyConstance.URI_MSG, null);
    }

    /**
     * 通过mark删除当前用户的所有的聊天信息
     *
     * @param owner
     */
    public void deleteMessageByOwner(String owner) {
        DataSupport.deleteAll(MessageBean.class, DBColumns.OWNER + " = ?", owner);
        // 发出通知，群组数据库发生变化了
        ctx.getContentResolver().notifyChange(MyConstance.URI_MSG, null);
    }

    /**
     * 删除所有的聊天消息
     */
    public void deleteAllMessage() {
        DataSupport.deleteAll(MessageBean.class);
        // 发出通知，群组数据库发生变化了
        ctx.getContentResolver().notifyChange(MyConstance.URI_MSG, null);
    }

    /**
     * 去重查询  正在聊天的会话
     */
    public List<MessageBean> queryConversation(String owner) {
        List<MessageBean> list = new ArrayList<>();
        if (owner != null) {
            Cursor cursor = DataSupport.findBySQL("select distinct * from " + DBColumns.TABLE_MSG + " where " + DBColumns.OWNER + " = ? group by " + DBColumns.MARK + " order by " + DBColumns.ID + " desc", owner);
            while (cursor.moveToNext()) {
                MessageBean bean = new MessageBean();
                bean.setFromUser(cursor.getString(cursor.getColumnIndex(DBColumns.FROM_USER)));
                bean.setToUser(cursor.getString(cursor.getColumnIndex(DBColumns.TO_USER)));
                bean.setDate(cursor.getLong(cursor.getColumnIndex(DBColumns.DATE)));
                bean.setBody(cursor.getString(cursor.getColumnIndex(DBColumns.BODY)));
                bean.setType(cursor.getInt(cursor.getColumnIndex(DBColumns.TYPE)));
                bean.setReceipt(cursor.getString(cursor.getColumnIndex(DBColumns.RECEIPT)));
                bean.setNick(cursor.getString(cursor.getColumnIndex(DBColumns.NICK)));
                bean.setAvatar(cursor.getString(cursor.getColumnIndex(DBColumns.AVATAR)));
                bean.setMark(cursor.getString(cursor.getColumnIndex(DBColumns.MARK)));
                list.add(bean);
            }
        }
        return list;
    }

    /**
     * 修改与指定好友的聊天消息为已读状态
     *
     * @param mark
     */
    public void updateMessageRead(String mark) {
        MessageBean messageBean = new MessageBean();
        messageBean.setIsRead("1");
        messageBean.updateAll(DBColumns.MARK + " = ?", mark);
        // 发出通知，群组数据库发生变化了
        ctx.getContentResolver().notifyChange(MyConstance.URI_MSG, null);
    }

    /**
     * 修改指定消息的回执状态
     *
     * @param stanzaId
     * @param receiptState
     */
    public void updateMessageReceipt(String stanzaId, String receiptState) {
        MessageBean messageBean = new MessageBean();
        messageBean.setReceipt(receiptState);
        messageBean.updateAll(DBColumns.STANZA_ID + " = ?", stanzaId);
        // 发出通知，群组数据库发生变化了
        ctx.getContentResolver().notifyChange(MyConstance.URI_MSG, null);
    }

    /**
     * 查询与指定好友的未读消息个数
     *
     * @param mark
     * @return
     */
    public int queryUnreadMessageCount(String mark) {
        return DataSupport.where(DBColumns.ISREAD + " = ? and " + DBColumns.MARK + " = ?", "0", mark).count(MessageBean.class);
    }

    /**
     * 查询指定消息的回执状态
     *
     * @param stanzaId 消息唯一标识
     * @return 消息状态   0 收到消息  1发送中 2已发送 3已送达 4发送失败
     */
    public String queryMessageReceipt(String stanzaId) {
        List<MessageBean> messageBeans = DataSupport.where(DBColumns.STANZA_ID + " = ?", stanzaId).select(DBColumns.RECEIPT).find(MessageBean.class);
        if (messageBeans != null && messageBeans.size() > 0) {
            return messageBeans.get(0).getReceipt();
        }
        return null;
    }
}
