package com.im.openimlib.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;

import com.im.openimlib.R;
import com.im.openimlib.Utils.MyBase64Utils;
import com.im.openimlib.Utils.MyConstance;
import com.im.openimlib.fragment.ConversationListFragment;
import com.im.openimlib.service.IMService;

public class MainActivity extends FragmentActivity {

    private FragmentManager fragmentManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences sp = getSharedPreferences(MyConstance.SP_NAME, 0);
        sp.edit().putString("username","lizhcf").apply();
        sp.edit().putString("password", MyBase64Utils.encodeToString("123123")).apply();
        initService();
        initView();
    }

    private void initService() {
        startService(new Intent(getApplicationContext(), IMService.class));
    }

    private void initView() {
        fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.main_frame, new ConversationListFragment()).commit();
    }
}
