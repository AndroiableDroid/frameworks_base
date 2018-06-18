/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.am;

import android.app.ActivityManager;
import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Message;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.Slog;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.R;
import com.android.internal.statusbar.ThemeAccentUtils;

import com.android.internal.util.custom.NavbarUtils;

/**
 *  Helper to manage showing/hiding a image to notify them that they are entering
 *  or exiting lock-to-app mode.
 */
public class LockTaskNotify {
    private static final String TAG = "LockTaskNotify";
    private static final long SHOW_TOAST_MINIMUM_INTERVAL = 1000;

    private final Context mContext;
    private final H mHandler;
    private Toast mLastToast;
    private long mLastShowToastTime;

    private IOverlayManager mOverlayManager;
    private int mCurrentUserId;

    public LockTaskNotify(Context context) {
        mContext = context;
        mHandler = new H();
        mOverlayManager = IOverlayManager.Stub.asInterface(ServiceManager.getService(mContext.OVERLAY_SERVICE));
        mCurrentUserId = ActivityManager.getCurrentUser();
    }

    public void showToast(int lockTaskModeState) {
        mHandler.obtainMessage(H.SHOW_TOAST, lockTaskModeState, 0 /* Not used */).sendToTarget();
    }

    public void handleShowToast(int lockTaskModeState) {
        String text = null;
        if (lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED) {
            text = mContext.getString(R.string.lock_to_app_toast_locked);
        } else if (lockTaskModeState == ActivityManager.LOCK_TASK_MODE_PINNED) {
            int msgId =  R.string.lock_to_app_toast;
            int screenPinningExitMode = mContext.getResources().getInteger(com.android.internal.R.integer.config_screenPinningExitMode);
            if (screenPinningExitMode == 1) {
                msgId = NavbarUtils.isNavigationBarEnabled(mContext) ? 
                                R.string.lock_to_app_toast_back_nav_visible :
                                R.string.lock_to_app_toast_back;
            }else if (screenPinningExitMode == 2) {
                msgId = NavbarUtils.isNavigationBarEnabled(mContext) ? 
                                R.string.lock_to_app_toast_power_nav_visible :
                                R.string.lock_to_app_toast_power;
            }
            text = mContext.getString(msgId);
        }
        if (text == null) {
            return;
        }
        long showToastTime = SystemClock.elapsedRealtime();
        if ((showToastTime - mLastShowToastTime) < SHOW_TOAST_MINIMUM_INTERVAL) {
            Slog.i(TAG, "Ignore toast since it is requested in very short interval.");
            return;
        }
        if (mLastToast != null) {
            mLastToast.cancel();
        }
        mLastToast = makeAllUserToastAndShow(text);
        mLastShowToastTime = showToastTime;
    }

    public void show(boolean starting) {
        int showString = R.string.lock_to_app_exit;
        if (starting) {
            showString = R.string.lock_to_app_start;
        }
        makeAllUserToastAndShow(mContext.getString(showString));
    }

    private Toast makeAllUserToastAndShow(String text) {
        Toast toast = Toast.makeText(mContext, text, Toast.LENGTH_LONG);
        toast.getWindowParams().privateFlags |=
                WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;

        View toastView = toast.getView();
        TextView message= toastView.findViewById(android.R.id.message);
        toastView.setBackgroundTintList(ColorStateList.valueOf(mContext.getResources().getColor(ThemeAccentUtils.isUsingDarkTheme(mOverlayManager, mCurrentUserId) ? R.color.screen_pinning_toast_dark_background_color : R.color.screen_pinning_toast_light_background_color)));
        message.setTextColor(ThemeAccentUtils.isUsingDarkTheme(mOverlayManager, mCurrentUserId) ? mContext.getColor(R.color.screen_pinning_toast_dark_text_color) : mContext.getColor(R.color.screen_pinning_toast_light_text_color));
        toast.show();
        return toast;
    }

    private final class H extends Handler {
        private static final int SHOW_TOAST = 3;

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case SHOW_TOAST:
                    handleShowToast(msg.arg1);
                    break;
            }
        }
    }
}
