package com.hungama.recordmedialibrary.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.TypedValue;


public class MiscUtils
{
    public static boolean isOrientationLandscape(Context context) {
        boolean isOrientationLandscape;
        int orientation = context.getResources().getConfiguration().orientation;
        switch (orientation) {
            case Configuration.ORIENTATION_LANDSCAPE:
                isOrientationLandscape = true;
                break;
            case Configuration.ORIENTATION_PORTRAIT:
            default:
                isOrientationLandscape = false;
        }
        return isOrientationLandscape;
    }

    public static float dpToPixelConvertor(float dp, Context context) {
        Resources r = context.getResources();
        float px = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                r.getDisplayMetrics()
        );

        return px;
    }
}
