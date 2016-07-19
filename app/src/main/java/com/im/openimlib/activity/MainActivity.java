package com.im.openimlib.activity;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;

import com.im.openimlib.R;
import com.im.openimlib.Utils.MResource;
import com.im.openimlib.app.OpenIM;
import com.im.openimlib.fragment.ConversationListFragment;

public class MainActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(MResource.getIdByName(getApplicationContext(),"layout","activity_main"));
        OpenIM.init(this,"lizhcf","123123");
        initView();
    }
    private void initView() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.main_frame, new ConversationListFragment()).commit();
    }
}
