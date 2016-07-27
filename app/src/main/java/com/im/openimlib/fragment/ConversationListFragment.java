package com.im.openimlib.fragment;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.im.openimlib.Utils.MResource;
import com.im.openimlib.Utils.MyConstance;
import com.im.openimlib.Utils.MyLog;
import com.im.openimlib.Utils.MyUtils;
import com.im.openimlib.Utils.ThreadUtil;
import com.im.openimlib.adapter.ConversationLVAdapter;
import com.im.openimlib.app.OpenIMApp;
import com.im.openimlib.bean.MessageBean;
import com.im.openimlib.dao.OpenIMDao;
import com.im.openimlib.view.MyDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天列表Fragment
 * Created by lzh12 on 2016/7/18.
 */
public class ConversationListFragment extends BaseFragment {
    private ListView mListView;
    private MyDialog pd;
    private List<MessageBean> list = new ArrayList<>();
    private OpenIMDao openIMDao;
    private static final int QUERY_SUCCESS = 100;
    private ConversationLVAdapter mAdapter;
    private FragmentActivity act;

    /**
     * 通过控件名称获取控件id
     * @param name
     * @return
     */
    private int getIdByName(String name) {
        return MResource.getIdByName(act, "id", name);
    }

    /**
     * 通过layout名称获取layout的id
     * @param layout
     * @return
     */
    private int getLayoutByName(String layout) {
        return MResource.getIdByName(act, "layout", layout);
    }

    /**
     * 通过style名称获取style的id
     * @param style
     * @return
     */
    private int getStyleByName(String style){
        return MResource.getIdByName(act,"style",style);
    }

    @Override
    public View initView() {
        act = getActivity();
        View view = View.inflate(act, getLayoutByName("fragment_conversation_list"), null);
        mListView = (ListView) view.findViewById(getIdByName("conversation_list"));
        return view;
    }

    @Override
    public void initData() {
        openIMDao = OpenIMDao.getInstance(act);
        pd = new MyDialog(act,getStyleByName("CustomProgressDialog"));
        pd.show();
        ThreadUtil.runOnBackThread(new Runnable() {
            @Override
            public void run() {
                list.clear();
                MyLog.showLog("owner::" + OpenIMApp.username);
                List<MessageBean> data = openIMDao.queryConversation(OpenIMApp.username);
                for (MessageBean messageBean : data) {
                    list.add(messageBean);
                }
                // 发送查询完成消息
                handler.sendEmptyMessage(QUERY_SUCCESS);
            }
        });

        /**
         * 不知道为嘛 cursorAdapter在activity外使用就不能自动更新了 所以在这儿写了个内容观察者，观察数据库的URL
         * 如果数据库发生变化 就改变cursor 然后让adapter刷新cursor
         */
        act.getContentResolver().registerContentObserver(MyConstance.URI_MSG, true, new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                ThreadUtil.runOnBackThread(new Runnable() {
                    @Override
                    public void run() {
                        MyLog.showLog("数据库改变::" + OpenIMApp.username);
                        List<MessageBean> data = openIMDao.queryConversation(OpenIMApp.username);
                        MyLog.showLog("data::" + data);
                        list.clear();
                        for (MessageBean messageBean : data) {
                            list.add(messageBean);
                        }
                        // 发送查询完成消息
                        handler.sendEmptyMessage(QUERY_SUCCESS);
                    }
                });
            }
        });
    }


    private Handler handler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                // 查询成功
                case QUERY_SUCCESS:
                    if (pd != null && pd.isShowing()) {
                        pd.dismiss();
                    }
                    if (mAdapter == null) {
                        mAdapter = new ConversationLVAdapter(act, list, 0);
                    } else {
                        // 这个要求adapter对应的list是同一个对象才能生效，不同对象不能生效
                        mAdapter.notifyDataSetChanged();
                        MyLog.showLog("数据库改变");
                    }
                    mListView.setAdapter(mAdapter);

                    /**
                     * 条目点击事件，跳转到聊天详情界面
                     */
                    mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            ListView listView = (ListView) parent;
                            MessageBean bean = (MessageBean) listView.getItemAtPosition(position);

                            String msgFrom = bean.getFromUser();
                            String friendNick = bean.getNick();
                            String msgTo = bean.getToUser();
                            String avatarUrl = bean.getAvatar();
                            String friendName;
                            if (msgFrom.equals(OpenIMApp.username)) {
                                friendName = msgTo;
                            } else {
                                friendName = msgFrom;
                            }
                            Intent intent = new Intent();
                            intent.setAction("com.openim.activity.chatactivity");
                            intent.addCategory(Intent.CATEGORY_DEFAULT);
                            intent.putExtra("friendName", friendName);
                            intent.putExtra("friendNick", friendNick);
                            intent.putExtra("avatarUrl", avatarUrl);
                            act.startActivity(intent);
                        }
                    });
                    mListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                        @Override
                        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                            MyLog.showLog("长按会话列表条目");
                            ListView listView = (ListView) parent;
                            MessageBean bean = (MessageBean) listView.getItemAtPosition(position);

                            String msgFrom = bean.getFromUser();
                            String friendNick = bean.getNick();
                            String msgTo = bean.getToUser();
                            String friendName;
                            if (msgFrom.equals(OpenIMApp.username)) {
                                friendName = msgTo;
                            } else {
                                friendName = msgFrom;
                            }
                            if (friendNick != null) {
                                showDialog(friendNick,friendName);
                            } else {
                                showDialog(friendName,friendName);
                            }
                            return true;
                        }
                    });
                    break;
            }
        }
    };
    private void showDialog(String friendNick, final String friendName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(act);
        final AlertDialog dialog = builder.create();
        View view = View.inflate(act, getLayoutByName("dialog_conversation"), null);
        TextView tvNick = (TextView) view.findViewById(getIdByName("tv_nick"));
        TextView tvTop = (TextView) view.findViewById(getIdByName("tv_top"));
        TextView tvDelete = (TextView) view.findViewById(getIdByName("tv_delete"));
        tvTop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MyUtils.showToast(act,"置顶该会话");
                dialog.dismiss();
            }
        });
        tvDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openIMDao.deleteMessageByMark(OpenIMApp.username + "#" + friendName);
                MyUtils.showToast(act, "删除该会话");
                dialog.dismiss();
            }
        });
        tvNick.setText(friendNick);
        dialog.setView(view, 0, 0, 0, 0);
        dialog.show();
    }
}
