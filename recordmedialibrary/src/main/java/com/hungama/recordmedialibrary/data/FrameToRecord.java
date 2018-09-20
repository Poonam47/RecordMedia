package com.hungama.recordmedialibrary.data;

import org.bytedeco.javacv.Frame;

public class FrameToRecord {
    private long timestamp;
    private Frame frame;

    public FrameToRecord(long timestamp, Frame frame) {
        this.timestamp = timestamp;
        this.frame = frame;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Frame getFrame() {
        return frame;
    }

    public void setFrame(Frame frame) {
        this.frame = frame;
    }
}
