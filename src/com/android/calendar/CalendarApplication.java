/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.calendar;

import android.app.Application;
import android.content.res.Configuration;
import com.sprd.calendar.lunar.LunarCalendar;
import com.sprd.calendar.lunar.LunarCalendarConvertUtil;
import com.sprd.calendar.foreigncalendar.ForeignFestivalCalendar;

public class CalendarApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        /*
         * Ensure the default values are set for any receiver, activity,
         * service, etc. of Calendar
         */
        GeneralPreferences.setDefaultValues(this);

        // Save the version number, for upcoming 'What's new' screen.  This will be later be
        // moved to that implementation.
        Utils.setSharedPreference(this, GeneralPreferences.KEY_VERSION,
                Utils.getVersionCode(this));

        // Initialize the registry mapping some custom behavior.
        ExtensionsFactory.init(getAssets());

        // SPRD: Modify for bug473571, add lunar info
        Utils.mLunarFlag = LunarCalendarConvertUtil.SUPPORT_LUNAR && LunarCalendarConvertUtil.isLunarSetting();
    }

    /* SPRD: Modify for bug473571, add lunar info @{ */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Utils.mLunarFlag = LunarCalendarConvertUtil.SUPPORT_LUNAR
                && LunarCalendarConvertUtil.isLunarSetting();
        if (LunarCalendarConvertUtil.isLunarSetting()) {
            LunarCalendar.reloadLanguageResources(this);
        } else {
            LunarCalendar.clearLanguageResourcesRefs();
        }
        /* @} */

        /* SPRD: Add for bug467636, add foreign festival info. @{ */
        if (Utils.mSupportForeignFestivalCalendar) {
            ForeignFestivalCalendar.reloadLanguageResources(this);
        } else {
            ForeignFestivalCalendar.clearLanguageResourcesRefs();
        }
        /* @} */
    }
}
