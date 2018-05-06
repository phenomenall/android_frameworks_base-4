/*
 * Copyright (C) 2018 CypherOS
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
 * limitations under the License
 */

package com.android.systemui.colormanager;

import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.os.RemoteException;
import android.provider.Settings;

/**
 * Helper class for Color Manager that works as a bridge
 * to get/set toxyc overlays.
 */
public class ColorManagerHelper {

    public static final String TAG = "ColorManagerHelper";

    public static final String[] BLACK_THEME = {
            "com.toxyc.theme.black",
            "com.toxyc.theme.settings.black",
    };
    public static final String[] DARK_THEME = {
            "com.toxyc.theme.dark",
            "com.toxyc.theme.settings.dark",
    };
    public static final String[] TOXYC_THEME = {
            "com.toxyc.theme.toxyc",
            "com.toxyc.theme.settings.toxyc",
    };

    // Accent Packages
    private static final String ACCENT_DEFAULT = "default";
    // Accent Packages: Blue
    private static final String ACCENT_BLUE = "com.toxyc.accent.blue";
    private static final String ACCENT_INDIGO = "com.toxyc.accent.indigo";
    private static final String ACCENT_OCEANIC = "com.toxyc.accent.oceanic";
    private static final String ACCENT_BRIGHTSKY = "com.toxyc.accent.brightsky";
    // Accent Packages: Green
    private static final String ACCENT_GREEN = "com.toxyc.accent.green";
    private static final String ACCENT_LIMA_BEAN = "com.toxyc.accent.limabean";
    private static final String ACCENT_LIME = "com.toxyc.accent.lime";
    private static final String ACCENT_TEAL = "com.toxyc.accent.teal";
    // Accent Packages: Pink
    private static final String ACCENT_PINK = "com.toxyc.accent.pink";
    private static final String ACCENT_PLAY_BOY = "com.toxyc.accent.playboy";
    // Accent Packages: Purple
    private static final String ACCENT_PURPLE = "com.toxyc.accent.purple";
    private static final String ACCENT_DEEP_VALLEY = "com.toxyc.accent.deepvalley";
    // Accent Packages: Red
    private static final String ACCENT_RED = "com.toxyc.accent.red";
    private static final String ACCENT_BLOODY_MARY = "com.toxyc.accent.bloodymary";
    // Accent Packages: Yellow
    private static final String ACCENT_YELLOW = "com.toxyc.accent.yellow";
    private static final String ACCENT_SUN_FLOWER = "com.toxyc.accent.sunflower";
    // Accent Packages: Other
    private static final String ACCENT_BLACK = "com.toxyc.accent.black";
    private static final String ACCENT_GREY = "com.toxyc.accent.grey";
    private static final String ACCENT_WHITE = "com.toxyc.accent.white";

    private static final String[] ACCENTS = {
            ACCENT_DEFAULT,
            ACCENT_BLUE,
            ACCENT_INDIGO,
            ACCENT_OCEANIC,
            ACCENT_BRIGHTSKY,
            ACCENT_GREEN,
            ACCENT_LIMA_BEAN,
            ACCENT_LIME,
            ACCENT_TEAL,
            ACCENT_PINK,
            ACCENT_PLAY_BOY,
            ACCENT_PURPLE,
            ACCENT_DEEP_VALLEY,
            ACCENT_RED,
            ACCENT_BLOODY_MARY,
            ACCENT_YELLOW,
            ACCENT_SUN_FLOWER,
            ACCENT_BLACK,
            ACCENT_GREY,
            ACCENT_WHITE,
    };

    public static boolean isUsingDarkTheme(IOverlayManager om, int userId) {
        OverlayInfo themeInfo = null;
        try {
            themeInfo = om.getOverlayInfo("com.toxyc.theme.dark",
                    userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return themeInfo != null && themeInfo.isEnabled();
    }

    public static boolean isUsingBlackTheme(IOverlayManager om, int userId) {
        OverlayInfo themeInfo = null;
        try {
            themeInfo = om.getOverlayInfo("com.toxyc.theme.black",
                    userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return themeInfo != null && themeInfo.isEnabled();
    }

    public static boolean isUsingToxycTheme(IOverlayManager om, int userId) {
        OverlayInfo themeInfo = null;
        try {
            themeInfo = om.getOverlayInfo("com.toxyc.theme.toxyc",
                    userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return themeInfo != null && themeInfo.isEnabled();
    }

    public static void restoreDefaultAccent(IOverlayManager om, int userId) {
        for (int i = 1; i < ACCENTS.length; i++) {
            String accents = ACCENTS[i];
            try {
                om.setEnabled(accents, false, userId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public static void updateAccent(IOverlayManager om, int userId, int userAccentSetting) throws RemoteException {
        switch (userAccentSetting) {
            case 0:
                restoreDefaultAccent(om, userId);
                break;
            case 1:
                om.setEnabledExclusive(ACCENT_BLUE, true, userId);
                break;
            case 2:
                om.setEnabledExclusive(ACCENT_INDIGO, true, userId);
                break;
            case 3:
                om.setEnabledExclusive(ACCENT_OCEANIC, true, userId);
                break;
            case 4:
                om.setEnabledExclusive(ACCENT_BRIGHTSKY, true, userId);
                break;
            case 5:
                om.setEnabledExclusive(ACCENT_GREEN, true, userId);
                break;
            case 6:
                om.setEnabledExclusive(ACCENT_LIMA_BEAN, true, userId);
                break;
            case 7:
                om.setEnabledExclusive(ACCENT_LIME, true, userId);
                break;
            case 8:
                om.setEnabledExclusive(ACCENT_TEAL, true, userId);
                break;
            case 9:
                om.setEnabledExclusive(ACCENT_PINK, true, userId);
                break;
            case 10:
                om.setEnabledExclusive(ACCENT_PLAY_BOY, true, userId);
                break;
            case 11:
                om.setEnabledExclusive(ACCENT_PURPLE, true, userId);
                break;
            case 12:
                om.setEnabledExclusive(ACCENT_DEEP_VALLEY, true, userId);
                break;
            case 13:
                om.setEnabledExclusive(ACCENT_RED, true, userId);
                break;
            case 14:
                om.setEnabledExclusive(ACCENT_BLOODY_MARY, true, userId);
                break;
            case 15:
                om.setEnabledExclusive(ACCENT_YELLOW, true, userId);
                break;
            case 16:
                om.setEnabledExclusive(ACCENT_SUN_FLOWER, true, userId);
                break;
            case 17:
                om.setEnabledExclusive(ACCENT_BLACK, true, userId);
                break;
            case 18:
                om.setEnabledExclusive(ACCENT_GREY, true, userId);
                break;
            case 19:
                om.setEnabledExclusive(ACCENT_WHITE, true, userId);
                break;
        }
    }
}