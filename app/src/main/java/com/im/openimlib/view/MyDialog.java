package com.im.openimlib.view;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.os.Looper;
import android.os.MessageQueue.IdleHandler;
import android.widget.ImageView;

import com.im.openimlib.Utils.MResource;


public class MyDialog extends Dialog {

	public MyDialog(Context context,int styleId) {
//		super(context, R.style.CustomProgressDialog);
		super(context,styleId);
	}

	/**
	 * 通过控件名称获取控件id
	 *
	 * @param name
	 * @return
	 */
	private int getIdByName(String name) {
		return MResource.getIdByName(getContext(), "id", name);
	}

	/**
	 * 通过layout名称获取layout的id
	 *
	 * @param layout
	 * @return
	 */
	private int getLayoutByName(String layout) {
		return MResource.getIdByName(getContext(), "layout", layout);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(getLayoutByName("openim_dialog_loading"));

		final ImageView imageView = (ImageView) findViewById(getIdByName("loadingImageView"));
		Looper.myQueue().addIdleHandler(new IdleHandler() {
			@Override
			public boolean queueIdle() {
				AnimationDrawable animationDrawable = (AnimationDrawable) imageView.getBackground();
				animationDrawable.start();
				return false;
			}
		});

	}
}
