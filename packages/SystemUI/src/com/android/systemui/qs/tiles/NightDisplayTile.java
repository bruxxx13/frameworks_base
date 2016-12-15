/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.tiles;

import android.app.ActivityManager;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Switch;

import com.android.internal.app.NightDisplayController;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;

import android.os.IPowerManager;
import android.os.AsyncTask;
import android.os.UserHandle;
import android.os.RemoteException;
import android.util.Log;
import android.os.ServiceManager;

public class NightDisplayTile extends QSTile<QSTile.BooleanState>
        implements NightDisplayController.Callback {

    private NightDisplayController mController;
    private boolean mIsListening;
    private float userAutoVal = Settings.System.getFloatForUser(mContext.getContentResolver(),
                                                        Settings.System.SCREEN_AUTO_BRIGHTNESS_ADJ, 0,
                                                        UserHandle.USER_CURRENT);
    private int userManualVal = Settings.System.getIntForUser(mContext.getContentResolver(),
                                                        Settings.System.SCREEN_BRIGHTNESS, 0,
                                                        UserHandle.USER_CURRENT);
    private boolean mAutomaticBrightness;

    public NightDisplayTile(Host host) {
        super(host);
        mController = new NightDisplayController(mContext, ActivityManager.getCurrentUser());
    }

    @Override
    public boolean isAvailable() {
        return NightDisplayController.isAvailable(mContext);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    private void setBrightness(boolean activated) {
        final float autoVal = -0.3f; //available from -1 to 1
        final int manualVal = 1; //available from 0 to 255
            if (activated) {
                updateBrightnessModeValues();
            }
            try {
                IPowerManager power = IPowerManager.Stub.asInterface(
                        ServiceManager.getService("power"));
                if (power != null) {
                    if (mAutomaticBrightness) {
                        power.setTemporaryScreenAutoBrightnessAdjustmentSettingOverride(autoVal);
                        AsyncTask.execute(new Runnable() {
                            public void run() {
                                if (activated) {
                                    Settings.System.putFloatForUser(mContext.getContentResolver(),
                                        Settings.System.SCREEN_AUTO_BRIGHTNESS_ADJ, autoVal,
                                        UserHandle.USER_CURRENT);
                                } else {
                                    Settings.System.putFloatForUser(mContext.getContentResolver(),
                                        Settings.System.SCREEN_AUTO_BRIGHTNESS_ADJ, userAutoVal,
                                        UserHandle.USER_CURRENT);
                                }
                            }
                        });
                    } else {
                        power.setTemporaryScreenBrightnessSettingOverride(manualVal);
                        AsyncTask.execute(new Runnable() {
                            @Override
                            public void run() {
                                if (activated) {
                                Settings.System.putIntForUser(mContext.getContentResolver(),
                                        Settings.System.SCREEN_BRIGHTNESS, manualVal,
                                        UserHandle.USER_CURRENT);
                                } else {
                                Settings.System.putIntForUser(mContext.getContentResolver(),
                                        Settings.System.SCREEN_BRIGHTNESS, userManualVal,
                                        UserHandle.USER_CURRENT);
                                }
                            }
                        });
                    }
                }
            } catch (RemoteException e) {
                Log.w(TAG, "Setting Brightness failed: " + e);
            }
    }

    public void updateBrightnessModeValues () {
        userAutoVal = Settings.System.getFloatForUser(mContext.getContentResolver(),
                                    Settings.System.SCREEN_AUTO_BRIGHTNESS_ADJ, 0,
                                    UserHandle.USER_CURRENT);
        userManualVal = Settings.System.getIntForUser(mContext.getContentResolver(),
                                    Settings.System.SCREEN_BRIGHTNESS, 0,
                                    UserHandle.USER_CURRENT);
        int mode = Settings.System.getIntForUser(mContext.getContentResolver(),
                                Settings.System.SCREEN_BRIGHTNESS_MODE,
                                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                                UserHandle.USER_CURRENT);
        mAutomaticBrightness = mode != Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;
    }

    public boolean isAutoNightTileEnabled() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
            Settings.Secure.QS_NIGHT_BRIGHTNESS_TOGGLE, 0) == 1;
    }

    @Override
    protected void handleClick() {
        final boolean activated = !mState.value;
        MetricsLogger.action(mContext, getMetricsCategory(), activated);
        mController.setActivated(activated);
        boolean autoNightTile = isAutoNightTileEnabled();
        if (autoNightTile) {
            setBrightness(activated);
        }
    }

    @Override
    protected void handleUserSwitch(int newUserId) {
        // Stop listening to the old controller.
        if (mIsListening) {
            mController.setListener(null);
        }

        // Make a new controller for the new user.
        mController = new NightDisplayController(mContext, newUserId);
        if (mIsListening) {
            mController.setListener(this);
        }

        super.handleUserSwitch(newUserId);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final boolean isActivated = mController.isActivated();
        state.value = isActivated;
        state.label = mContext.getString(R.string.quick_settings_night_display_label);
        state.icon = ResourceIcon.get(isActivated ? R.drawable.ic_qs_night_display_on
                : R.drawable.ic_qs_night_display_off);
        state.contentDescription = mContext.getString(isActivated
                ? R.string.quick_settings_night_display_summary_on
                : R.string.quick_settings_night_display_summary_off);
        state.minimalAccessibilityClassName = state.expandedAccessibilityClassName
                = Switch.class.getName();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_NIGHT_DISPLAY;
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(Settings.ACTION_NIGHT_DISPLAY_SETTINGS);
    }

    @Override
    protected void setListening(boolean listening) {
        mIsListening = listening;
        if (listening) {
            mController.setListener(this);
            refreshState();
        } else {
            mController.setListener(null);
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_night_display_label);
    }

    @Override
    public void onActivated(boolean activated) {
        refreshState();
    }
}
