package com.blankj.utilcode.util;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

/**
 * <pre>
 *     author: blankj
 *     blog  : http://blankj.com
 *     time  : 2019/06/18
 *     desc  : utils about view
 * </pre>
 */
public class ViewUtils {

    public static void setViewEnabled(View view, boolean enabled) {
        setViewEnabled(view, enabled, (View) null);
    }

    public static void setViewEnabled(View view, boolean enabled, View... excludes) {
        if (view == null) return;
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            int childCount = viewGroup.getChildCount();
            for (int i = 0; i < childCount; i++) {
                setViewEnabled(viewGroup.getChildAt(i), enabled, excludes);
            }
        }
        if (excludes != null) {
            for (View exclude : excludes) {
                if (view == exclude) return;
            }
        }
        view.setEnabled(enabled);
    }

    /**
     * add by codemx
     * 判断是否滑动到屏幕中
     *
     * 在onScrollChanged方法中调用
     *
     * @param view 视图
     * @return 是否显示在屏幕中
     */
    public static boolean isViewScrollToScreen(View view) {
        Rect rect = new Rect();
        view.getLocalVisibleRect(rect);
        int top = rect.top;
        int bottom = rect.bottom;
        return top >= 0 && bottom > 0 && top < bottom;
    }

}