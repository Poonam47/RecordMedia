package com.hungama.recordmedialibrary.utils;

public abstract class DefaultCallback implements MediaController.Callbacks {

    @Override
    public void onImagePickerError(Exception e, MediaController.ImageSource source, int type) {
    }

    @Override
    public void onCanceled(MediaController.ImageSource source, int type) {
    }
}
