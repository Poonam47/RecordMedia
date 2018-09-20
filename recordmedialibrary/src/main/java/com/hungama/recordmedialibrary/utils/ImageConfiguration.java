package com.hungama.recordmedialibrary.utils;

import android.content.Context;
import android.preference.PreferenceManager;

public class ImageConfiguration implements Constants {

    private Context context;

    public ImageConfiguration(Context context) {
        this.context = context;
    }

    public ImageConfiguration setImagesFolderName(String folderName) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putString(BundleKeys.FOLDER_NAME, folderName)
                .commit();
        return this;
    }

    public ImageConfiguration setAllowMultiplePickInGallery(boolean allowMultiple) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(BundleKeys.ALLOW_MULTIPLE, allowMultiple)
                .commit();
        return this;
    }

    public ImageConfiguration setCopyTakenPhotosToPublicGalleryAppFolder(boolean copy) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(BundleKeys.COPY_TAKEN_PHOTOS, copy)
                .commit();
        return this;
    }

    public ImageConfiguration setCopyPickedImagesToPublicGalleryAppFolder(boolean copy) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(BundleKeys.COPY_PICKED_IMAGES, copy)
                .commit();
        return this;
    }
    public ImageConfiguration setCopyPickedVideosToPublicGalleryAppFolder(boolean copy) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(BundleKeys.COPY_PICKED_VIDEOS, copy)
                .commit();
        return this;
    }

    public String getFolderName() {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(BundleKeys.FOLDER_NAME, DEFAULT_FOLDER_NAME);
    }

    public boolean allowsMultiplePickingInGallery() {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(BundleKeys.ALLOW_MULTIPLE, false);
    }

    public boolean shouldCopyTakenPhotosToPublicGalleryAppFolder() {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(BundleKeys.COPY_TAKEN_PHOTOS, false);
    }

    public boolean shouldCopyPickedImagesToPublicGalleryAppFolder() {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(BundleKeys.COPY_PICKED_IMAGES, false);
    }
    public boolean shouldCopyPickedVideosToPublicGalleryAppFolder() {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(BundleKeys.COPY_PICKED_VIDEOS, false);
    }

}
