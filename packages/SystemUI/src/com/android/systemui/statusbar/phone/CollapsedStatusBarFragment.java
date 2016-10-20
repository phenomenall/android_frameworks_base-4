/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.statusbar.phone;

import static android.app.StatusBarManager.DISABLE_NOTIFICATION_ICONS;
import static android.app.StatusBarManager.DISABLE_SYSTEM_INFO;

import static com.android.systemui.statusbar.phone.StatusBar.reinflateSignalCluster;

import android.annotation.Nullable;
import android.app.Fragment;
import android.app.StatusBarManager;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.LinearLayout;

import com.android.systemui.Dependency;
import com.android.systemui.Interpolators;
import com.android.systemui.R;
import com.android.systemui.SysUiServiceProvider;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.SignalClusterView;
import com.android.systemui.statusbar.phone.StatusBarIconController.DarkIconManager;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.statusbar.policy.DarkIconDispatcher;
import com.android.systemui.statusbar.policy.EncryptionHelper;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.SignalCallback;

/**
 * Contains the collapsed status bar and handles hiding/showing based on disable flags
 * and keyguard state. Also manages lifecycle to make sure the views it contains are being
 * updated by the StatusBarIconController and DarkIconManager while it is attached.
 */
public class CollapsedStatusBarFragment extends Fragment implements CommandQueue.Callbacks {

    public static final String TAG = "CollapsedStatusBarFragment";
    private static final String EXTRA_PANEL_STATE = "panel_state";

    private static final int CLOCK_DATE_POSITION_DEFAULT  = 0;
    private static final int CLOCK_DATE_POSITION_CENTERED = 1;
    private static final int CLOCK_DATE_POSITION_HIDDEN   = 2;
    private static final int CLOCK_DATE_POSITION_LEFT   = 3;

    private PhoneStatusBarView mStatusBar;
    private KeyguardMonitor mKeyguardMonitor;
    private NetworkController mNetworkController;
    private LinearLayout mSystemIconArea;
    private View mNotificationIconAreaInner;
    private int mDisabled1;
    private StatusBar mStatusBarComponent;
    private DarkIconManager mDarkIconManager;
    private SignalClusterView mSignalClusterView;
    private Clock mClockDefault;
    private Clock mClockCentered;
    private Clock mClockLeft;
    private View mCenterClockLayout;

    private ContentResolver mResolver;

    private int mClockPosition = CLOCK_DATE_POSITION_DEFAULT;

    // Custom Carrier
    private View mCustomCarrierLabel;
    private int mShowCarrierLabel;

    private final Handler mHandler = new Handler();

    private class CustomSettingsObserver extends ContentObserver {
        CustomSettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            getContext().getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CARRIER),
                    false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings(true);
        }
    }
    private CustomSettingsObserver mCustomSettingsObserver = new CustomSettingsObserver(mHandler);

    private SignalCallback mSignalCallback = new SignalCallback() {
        @Override
        public void setIsAirplaneMode(NetworkController.IconState icon) {
            mStatusBarComponent.recomputeDisableFlags(true /* animate */);
        }
    };

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = getContext().getContentResolver();

            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_DATE_POSITION),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_SHOW_SECONDS),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_SHOW_DATE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_DATE_FORMAT),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_DATE_STYLE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_DATE_SIZE_SMALL),
                    false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_DATE_POSITION))) {
                updateClockDatePosition();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_SHOW_SECONDS))) {
                updateClockShowSeconds();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_SHOW_DATE))) {
                updateClockShowDate();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_DATE_FORMAT))) {
                updateClockDateFormat();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_DATE_STYLE))) {
                updateClockDateStyle();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_DATE_SIZE_SMALL))) {
                updateClockShowDateSizeSmall();
            }
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mKeyguardMonitor = Dependency.get(KeyguardMonitor.class);
        mNetworkController = Dependency.get(NetworkController.class);
        mStatusBarComponent = SysUiServiceProvider.getComponent(getContext(), StatusBar.class);
        SettingsObserver observer = new SettingsObserver(new Handler());
        observer.observe();
        mResolver = getContext().getContentResolver();
        mCustomSettingsObserver.observe();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.status_bar, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mStatusBar = (PhoneStatusBarView) view;
        if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_PANEL_STATE)) {
            mStatusBar.go(savedInstanceState.getInt(EXTRA_PANEL_STATE));
        }
        mDarkIconManager = new DarkIconManager(view.findViewById(R.id.statusIcons));
        Dependency.get(StatusBarIconController.class).addIconGroup(mDarkIconManager);
        mSystemIconArea = mStatusBar.findViewById(R.id.system_icon_area);
        mSignalClusterView = mStatusBar.findViewById(R.id.signal_cluster);
        mClockDefault = (Clock) mStatusBar.findViewById(R.id.clock);
        mClockCentered = (Clock) mStatusBar.findViewById(R.id.center_clock);
        mClockLeft = (Clock) mStatusBar.findViewById(R.id.left_clock);
        mCenterClockLayout = mStatusBar.findViewById(R.id.center_clock_layout);
        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(mSignalClusterView);
        mCustomCarrierLabel = mStatusBar.findViewById(R.id.statusbar_carrier_text);
        updateSettings(false);
        // Default to showing until we know otherwise.
        showSystemIconArea(false);
        initEmergencyCryptkeeperText();
        setUpClock();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(EXTRA_PANEL_STATE, mStatusBar.getState());
    }

    @Override
    public void onResume() {
        super.onResume();
        SysUiServiceProvider.getComponent(getContext(), CommandQueue.class).addCallbacks(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        SysUiServiceProvider.getComponent(getContext(), CommandQueue.class).removeCallbacks(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(mSignalClusterView);
        Dependency.get(StatusBarIconController.class).removeIconGroup(mDarkIconManager);
        if (mNetworkController.hasEmergencyCryptKeeperText()) {
            mNetworkController.removeCallback(mSignalCallback);
        }
    }

    public void initNotificationIconArea(NotificationIconAreaController
            notificationIconAreaController) {
        ViewGroup notificationIconArea = mStatusBar.findViewById(R.id.notification_icon_area);
        mNotificationIconAreaInner =
                notificationIconAreaController.getNotificationInnerAreaView();
        if (mNotificationIconAreaInner.getParent() != null) {
            ((ViewGroup) mNotificationIconAreaInner.getParent())
                    .removeView(mNotificationIconAreaInner);
        }
        notificationIconArea.addView(mNotificationIconAreaInner);
        // Default to showing until we know otherwise.
        showNotificationIconArea(false);
    }

    @Override
    public void disable(int state1, int state2, boolean animate) {
        state1 = adjustDisableFlags(state1);
        final int old1 = mDisabled1;
        final int diff1 = state1 ^ old1;
        mDisabled1 = state1;
        if ((diff1 & DISABLE_SYSTEM_INFO) != 0) {
            if ((state1 & DISABLE_SYSTEM_INFO) != 0) {
                hideSystemIconArea(animate);
            } else {
                showSystemIconArea(animate);
            }
        }
        if ((diff1 & DISABLE_NOTIFICATION_ICONS) != 0) {
            if ((state1 & DISABLE_NOTIFICATION_ICONS) != 0) {
                hideNotificationIconArea(animate);
            } else {
                showNotificationIconArea(animate);
            }
        }
    }

    protected int adjustDisableFlags(int state) {
        if (!mStatusBarComponent.isLaunchTransitionFadingAway()
                && !mKeyguardMonitor.isKeyguardFadingAway()
                && shouldHideNotificationIcons()) {
            state |= DISABLE_NOTIFICATION_ICONS;
            state |= DISABLE_SYSTEM_INFO;
        }
        if (mNetworkController != null && EncryptionHelper.IS_DATA_ENCRYPTED) {
            if (mNetworkController.hasEmergencyCryptKeeperText()) {
                state |= DISABLE_NOTIFICATION_ICONS;
            }
            if (!mNetworkController.isRadioOn()) {
                state |= DISABLE_SYSTEM_INFO;
            }
        }
        return state;
    }

    private boolean shouldHideNotificationIcons() {
        if (!mStatusBar.isClosed() && mStatusBarComponent.hideStatusBarIconsWhenExpanded()) {
            return true;
        }
        if (mStatusBarComponent.hideStatusBarIconsForBouncer()) {
            return true;
        }
        return false;
    }

    public void hideSystemIconArea(boolean animate) {
        animateHide(mSystemIconArea, animate);
        if (mClockPosition == CLOCK_DATE_POSITION_CENTERED) {
            animateHide(mCenterClockLayout, animate);
        }
        if (mClockPosition == CLOCK_DATE_POSITION_LEFT) {
            animateHide(mClockLeft, animate);
        }
    }

    public void showSystemIconArea(boolean animate) {
        animateShow(mSystemIconArea, animate);
        if (mClockPosition == CLOCK_DATE_POSITION_CENTERED) {
            animateShow(mCenterClockLayout, animate);
        }
        if (mClockPosition == CLOCK_DATE_POSITION_LEFT) {
            animateShow(mClockLeft, animate);
        }
    }

    public void hideNotificationIconArea(boolean animate) {
        animateHide(mNotificationIconAreaInner, animate);
    }

    public void showNotificationIconArea(boolean animate) {
        animateShow(mNotificationIconAreaInner, animate);
    }

    /**
     * Hides a view.
     */
    private void animateHide(final View v, boolean animate) {
        v.animate().cancel();
        if (!animate) {
            v.setAlpha(0f);
            v.setVisibility(View.INVISIBLE);
            return;
        }
        v.animate()
                .alpha(0f)
                .setDuration(160)
                .setStartDelay(0)
                .setInterpolator(Interpolators.ALPHA_OUT)
                .withEndAction(() -> v.setVisibility(View.INVISIBLE));
    }

    /**
     * Shows a view, and synchronizes the animation with Keyguard exit animations, if applicable.
     */
    private void animateShow(View v, boolean animate) {
        v.animate().cancel();
        v.setVisibility(View.VISIBLE);
        if (!animate) {
            v.setAlpha(1f);
            return;
        }
        v.animate()
                .alpha(1f)
                .setDuration(320)
                .setInterpolator(Interpolators.ALPHA_IN)
                .setStartDelay(50)

                // We need to clean up any pending end action from animateHide if we call
                // both hide and show in the same frame before the animation actually gets started.
                // cancel() doesn't really remove the end action.
                .withEndAction(null);

        // Synchronize the motion with the Keyguard fading if necessary.
        if (mKeyguardMonitor.isKeyguardFadingAway()) {
            v.animate()
                    .setDuration(mKeyguardMonitor.getKeyguardFadingAwayDuration())
                    .setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN)
                    .setStartDelay(mKeyguardMonitor.getKeyguardFadingAwayDelay())
                    .start();
        }
    }

    private void initEmergencyCryptkeeperText() {
        View emergencyViewStub = mStatusBar.findViewById(R.id.emergency_cryptkeeper_text);
        if (mNetworkController.hasEmergencyCryptKeeperText()) {
            if (emergencyViewStub != null) {
                ((ViewStub) emergencyViewStub).inflate();
            }
            mNetworkController.addCallback(mSignalCallback);
        } else if (emergencyViewStub != null) {
            ViewGroup parent = (ViewGroup) emergencyViewStub.getParent();
            parent.removeView(emergencyViewStub);
        }
    }

    private void setUpClock() {
        updateClockDatePosition();
        updateClockShowSeconds();
        updateClockShowDate();
        updateClockDateFormat();
        updateClockDateStyle();
        updateClockShowDateSizeSmall();
    }

    private void updateClockDatePosition() {
        int position =  Settings.System.getInt(mResolver,
			    Settings.System.STATUS_BAR_CLOCK_DATE_POSITION, CLOCK_DATE_POSITION_DEFAULT);

        if (mClockPosition != position) {
            mClockPosition = position;

            switch (mClockPosition) {
                case CLOCK_DATE_POSITION_DEFAULT:
                    mClockDefault.setClockVisibleByUser(true);
                    mCenterClockLayout.setVisibility(View.GONE);
                    mClockCentered.setClockVisibleByUser(false);
                    mClockLeft.setClockVisibleByUser(false);
                    break;
                case CLOCK_DATE_POSITION_CENTERED:
                    mClockDefault.setClockVisibleByUser(false);
                    mCenterClockLayout.setVisibility(View.VISIBLE);
                    mClockCentered.setClockVisibleByUser(true);
                    mClockLeft.setClockVisibleByUser(false);
                    break;
                case CLOCK_DATE_POSITION_LEFT:
                    mClockDefault.setClockVisibleByUser(false);
                    mCenterClockLayout.setVisibility(View.GONE);
                    mClockCentered.setClockVisibleByUser(false);
                    mClockLeft.setClockVisibleByUser(true);
                    break;
                case CLOCK_DATE_POSITION_HIDDEN:
                    mClockDefault.setClockVisibleByUser(false);
                    mCenterClockLayout.setVisibility(View.GONE);
                    mClockCentered.setClockVisibleByUser(false);
                    mClockLeft.setClockVisibleByUser(false);
                    break;
            }
        }
    }

    private void updateClockShowSeconds() {
        boolean show = Settings.System.getInt(mResolver,
			    Settings.System.STATUS_BAR_CLOCK_SHOW_SECONDS, 0) == 1;
        mClockDefault.setShowSeconds(show);
        mClockCentered.setShowSeconds(show);
        mClockLeft.setShowSeconds(show);
    }

    private void updateClockShowDate() {
        boolean show = Settings.System.getInt(mResolver,
			    Settings.System.STATUS_BAR_CLOCK_SHOW_DATE, 0) == 1;
        mClockDefault.setShowDate(show);
        mClockCentered.setShowDate(show);
        mClockLeft.setShowDate(show);
    }

    private void updateClockDateFormat() {
        String format = Settings.System.getString(mResolver,
                Settings.System.STATUS_BAR_CLOCK_DATE_FORMAT);
        mClockDefault.setDateFormat(format);
        mClockCentered.setDateFormat(format);
        mClockLeft.setDateFormat(format);
    }

    private void updateClockDateStyle() {
        int style = Settings.System.getInt(mResolver,
			    Settings.System.STATUS_BAR_CLOCK_DATE_STYLE, Clock.DATE_STYLE_REGULAR);
        mClockDefault.setDateStyle(style);
        mClockCentered.setDateStyle(style);
        mClockLeft.setDateStyle(style);
    }

    private void updateClockShowDateSizeSmall() {
        boolean small = Settings.System.getInt(mResolver,
			    Settings.System.STATUS_BAR_CLOCK_DATE_SIZE_SMALL, 0) == 1;
        mClockDefault.setShowDateSizeSmall(small);
        mClockCentered.setShowDateSizeSmall(small);
        mClockLeft.setShowDateSizeSmall(small);
    }

    public void updateSettings(boolean animate) {
        mShowCarrierLabel = Settings.System.getIntForUser(
                getContext().getContentResolver(), Settings.System.STATUS_BAR_CARRIER, 1,
                UserHandle.USER_CURRENT);
    }
}