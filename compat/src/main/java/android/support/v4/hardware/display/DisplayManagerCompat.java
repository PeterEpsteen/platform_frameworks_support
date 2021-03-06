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

package android.support.v4.hardware.display;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.view.Display;
import android.view.WindowManager;

import java.util.WeakHashMap;

/**
 * Helper for accessing features in {@link android.hardware.display.DisplayManager}.
 */
public abstract class DisplayManagerCompat {
    private static final WeakHashMap<Context, DisplayManagerCompat> sInstances =
            new WeakHashMap<Context, DisplayManagerCompat>();

    /**
     * Display category: Presentation displays.
     * <p>
     * This category can be used to identify secondary displays that are suitable for
     * use as presentation displays.
     * </p>
     *
     * @see android.app.Presentation for information about presenting content
     * on secondary displays.
     * @see #getDisplays(String)
     */
    public static final String DISPLAY_CATEGORY_PRESENTATION =
            "android.hardware.display.category.PRESENTATION";

    DisplayManagerCompat() {
    }

    /**
     * Gets an instance of the display manager given the context.
     */
    @NonNull
    public static DisplayManagerCompat getInstance(@NonNull Context context) {
        synchronized (sInstances) {
            DisplayManagerCompat instance = sInstances.get(context);
            if (instance == null) {
                if (Build.VERSION.SDK_INT >= 17) {
                    instance = new DisplayManagerCompatApi17Impl(context);
                } else {
                    instance = new DisplayManagerCompatApi14Impl(context);
                }
                sInstances.put(context, instance);
            }
            return instance;
        }
    }

    /**
     * Gets information about a logical display.
     *
     * The display metrics may be adjusted to provide compatibility
     * for legacy applications.
     *
     * @param displayId The logical display id.
     * @return The display object, or null if there is no valid display with the given id.
     */
    @Nullable
    public abstract Display getDisplay(int displayId);

    /**
     * Gets all currently valid logical displays.
     *
     * @return An array containing all displays.
     */
    @NonNull
    public abstract Display[] getDisplays();

    /**
     * Gets all currently valid logical displays of the specified category.
     * <p>
     * When there are multiple displays in a category the returned displays are sorted
     * of preference.  For example, if the requested category is
     * {@link #DISPLAY_CATEGORY_PRESENTATION} and there are multiple presentation displays
     * then the displays are sorted so that the first display in the returned array
     * is the most preferred presentation display.  The application may simply
     * use the first display or allow the user to choose.
     * </p>
     *
     * @param category The requested display category or null to return all displays.
     * @return An array containing all displays sorted by order of preference.
     *
     * @see #DISPLAY_CATEGORY_PRESENTATION
     */
    @NonNull
    public abstract Display[] getDisplays(String category);

    private static class DisplayManagerCompatApi14Impl extends DisplayManagerCompat {
        private final WindowManager mWindowManager;

        DisplayManagerCompatApi14Impl(Context context) {
            mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        }

        @Override
        public Display getDisplay(int displayId) {
            Display display = mWindowManager.getDefaultDisplay();
            if (display.getDisplayId() == displayId) {
                return display;
            }
            return null;
        }

        @Override
        public Display[] getDisplays() {
            return new Display[] { mWindowManager.getDefaultDisplay() };
        }

        @Override
        public Display[] getDisplays(String category) {
            return category == null ? getDisplays() : new Display[0];
        }
    }

    @RequiresApi(17)
    private static class DisplayManagerCompatApi17Impl extends DisplayManagerCompat {
        private final DisplayManager mDisplayManager;

        DisplayManagerCompatApi17Impl(Context context) {
            mDisplayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        }

        @Override
        public Display getDisplay(int displayId) {
            return mDisplayManager.getDisplay(displayId);
        }

        @Override
        public Display[] getDisplays() {
            return mDisplayManager.getDisplays();
        }

        @Override
        public Display[] getDisplays(String category) {
            return mDisplayManager.getDisplays(category);
        }
    }
}
