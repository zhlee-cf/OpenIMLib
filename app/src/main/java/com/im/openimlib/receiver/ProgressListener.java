package com.im.openimlib.receiver;

/**
 * 图片上传进度监听器接口
 */
public interface ProgressListener {
	void transferred(long transferredBytes);
}
