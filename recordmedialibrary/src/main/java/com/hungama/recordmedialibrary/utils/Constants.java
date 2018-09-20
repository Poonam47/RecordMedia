package com.hungama.recordmedialibrary.utils;

public interface Constants
{
    String DEFAULT_FOLDER_NAME = "Artistaloud";

    interface RequestCodes {
        int EASYIMAGE_IDENTIFICATOR = 0b1101101100; //876
        int SOURCE_CHOOSER = 1 << 15;

        int PICK_PICTURE_FROM_DOCUMENTS = EASYIMAGE_IDENTIFICATOR + (1 << 11);
        int PICK_PICTURE_FROM_GALLERY = EASYIMAGE_IDENTIFICATOR + (1 << 12);
        int TAKE_PICTURE = EASYIMAGE_IDENTIFICATOR + (1 << 13);
        int CAPTURE_VIDEO = EASYIMAGE_IDENTIFICATOR + (1 << 14);
    }

    interface BundleKeys {
        String FOLDER_NAME = "recordedmedia.folder_name";
        String ALLOW_MULTIPLE = "recordedmedia.allow_multiple";
        String COPY_TAKEN_PHOTOS = "recordedmedia.copy_taken_photos";
        String COPY_PICKED_IMAGES = "recordedmedia.copy_picked_images";
        String COPY_PICKED_VIDEOS = "recordedmedia.copy_picked_videos";
    }
}
