package com.im.openimlib.adapter;

import android.content.Context;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.im.openimlib.Utils.MResource;
import com.im.openimlib.Utils.MyBitmapUtils;
import com.im.openimlib.bean.MessageBean;
import com.im.openimlib.dao.OpenIMDao;
import com.im.openimlib.view.CircularImage;

import java.util.List;


public class ConversationLVAdapter extends BaseAdapter {

    private final MyBitmapUtils bitmapUtils;
    private final OpenIMDao openIMDao;

    public ConversationLVAdapter(Context ctx, List<MessageBean> data, int rightWidth) {
        this.ctx = ctx;
        this.data = data;
        openIMDao = OpenIMDao.getInstance(ctx);
        bitmapUtils = new MyBitmapUtils(ctx);
    }

    /**
     * 上下文对象
     */
    private Context ctx;
    private List<MessageBean> data;

    static class ViewHolder {
        RelativeLayout item_left;

        TextView tv_title;
        TextView tv_msg;
        TextView tv_time;
        CircularImage iv_icon;

        TextView tv_unread;
    }

    /**
     * 通过控件名称获取控件id
     * @param name
     * @return
     */
    private int getIdByName(String name) {
        return MResource.getIdByName(ctx, "id", name);
    }

    /**
     * 通过layout名称获取layout的id
     * @param layout
     * @return
     */
    private int getLayoutByName(String layout) {
        return MResource.getIdByName(ctx, "layout", layout);
    }

    /**
     * 通过drawable名称找到drawable
     * @param drawable
     * @return
     */
    private int getDrawableByName(String drawable){
        return MResource.getIdByName(ctx,"drawable",drawable);
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public Object getItem(int position) {
        return data.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        View view;
        ViewHolder vh;
        if (convertView == null) {
            vh = new ViewHolder();
            view = View.inflate(ctx, getLayoutByName("openim_list_item_conversation"), null);
            vh.item_left = (RelativeLayout) view.findViewById(getIdByName("item_left"));
            vh.iv_icon = (CircularImage) view.findViewById(getIdByName("iv_icon"));
            vh.iv_icon.setTag(position);
            vh.tv_title = (TextView) view.findViewById(getIdByName("tv_title"));
            vh.tv_msg = (TextView) view.findViewById(getIdByName("tv_msg"));
            vh.tv_time = (TextView) view.findViewById(getIdByName("tv_time"));
            vh.tv_unread = (TextView) view.findViewById(getIdByName("tv_unread_num"));
            view.setTag(vh);
        } else {
            view = convertView;
            vh = (ViewHolder) view.getTag();
        }
        vh.item_left.setPressed(false);
        MessageBean bean = data.get(position);

        String msgBody = bean.getBody().trim();
        int msgType = bean.getType();
        String msgReceipt = bean.getReceipt();
        String msgNick = bean.getNick();
        String msgAvatar = bean.getAvatar();
        // 显示时间 如果是今天 则只显示时间
        // 如果不是今天 则显示日期
        Long msgDateLong = bean.getDate();
        vh.tv_title.setText(msgNick);
        if (msgAvatar != null){
            bitmapUtils.display(vh.iv_icon,msgAvatar);
        } else {
            vh.iv_icon.setImageResource(getDrawableByName("ic_launcher"));
        }
        if ("-1".equals(msgReceipt)) {
            vh.tv_msg.setText("【发送失败】");
        } else if ("1".equals(msgReceipt)) {
            vh.tv_msg.setText("【发送中...】");
        } else {
            if (msgType == 0) {
                vh.tv_msg.setText(msgBody);
            } else if (msgType == 1) {
                vh.tv_msg.setText("【图片】");
            } else if (msgType == 2) {
                vh.tv_msg.setText("【语音】");
            } else if (msgType == 3) {
                vh.tv_msg.setText("【位置】");
            }
        }

        String msgDate;
        if (DateUtils.isToday(msgDateLong)) { // 判断是否是今天
            msgDate = DateFormat.getTimeFormat(ctx).format(msgDateLong);
        } else {
            msgDate = DateFormat.getDateFormat(ctx).format(msgDateLong);
        }
        vh.tv_time.setText(msgDate);

        // 查询此条目的未读消息个数 并显示
        int unreadMsgCount = openIMDao.queryUnreadMessageCount(bean.getMark());
        if (unreadMsgCount == 0) {
            vh.tv_unread.setVisibility(View.GONE);
        } else {
            vh.tv_unread.setVisibility(View.VISIBLE);
            vh.tv_unread.setText(unreadMsgCount + "");
        }
        return view;
    }
}
