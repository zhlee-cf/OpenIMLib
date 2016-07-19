package com.im.openimlib.Utils;

import android.content.Context;

/**
 * 获取资源ID工具类
 */
public class MResource {
    /**
     * 方法  通过资源名称和类型和包名获取资源id
     *
     * @param context   上下文对象
     * @param className 资源类型  如layout string
     * @param name      资源名称 如tv_name
     * @return
     */
    public static int getIdByName(Context context, String className, String name) {
        String packageName = context.getPackageName();
        Class r;
        int id = 0;
        try {
            r = Class.forName(packageName + ".R");

            Class[] classes = r.getClasses();
            Class desireClass = null;

            for (int i = 0; i < classes.length; ++i) {
                if (classes[i].getName().split("\\$")[1].equals(className)) {
                    desireClass = classes[i];
                    break;
                }
            }

            if (desireClass != null)
                id = desireClass.getField(name).getInt(desireClass);
        } catch (ClassNotFoundException | IllegalArgumentException | SecurityException | IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }

        return id;
    }
}
