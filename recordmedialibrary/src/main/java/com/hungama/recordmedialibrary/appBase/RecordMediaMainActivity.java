package com.hungama.recordmedialibrary.appBase;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import com.hungama.recordmedialibrary.R;
import com.hungama.recordmedialibrary.customviews.FixedRatioCroppedTextureView;
import com.hungama.recordmedialibrary.data.FrameToRecord;
import com.hungama.recordmedialibrary.data.RecordFragment;
import com.hungama.recordmedialibrary.utils.CameraHelper;
import com.hungama.recordmedialibrary.utils.Constants;
import com.hungama.recordmedialibrary.utils.DefaultCallback;
import com.hungama.recordmedialibrary.utils.MediaController;
import com.hungama.recordmedialibrary.utils.MiscUtils;

import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacv.FFmpegFrameFilter;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameFilter;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.LinkedBlockingQueue;

import static java.lang.Thread.State.WAITING;

public class RecordMediaMainActivity extends AppCompatActivity implements
        TextureView.SurfaceTextureListener, View.OnClickListener
{
    private static final String LOG_TAG = RecordMediaMainActivity.class.getSimpleName();

    private static final int REQUEST_PERMISSIONS = 1;

    private static final int PREFERRED_PREVIEW_WIDTH = 640;
    private static final int PREFERRED_PREVIEW_HEIGHT = 480;

    // both in milliseconds
    private static final long MIN_VIDEO_LENGTH = 1 * 1000;
    private static final long MAX_VIDEO_LENGTH = 90 * 1000;

    private FixedRatioCroppedTextureView mPreview;
    private ImageView mBtnResumeOrPause;
    private ImageView mBtnDone;
    private ImageView mBtnSwitchCamera;
    private ImageView mBtnReset;

    private int mCameraId;
    private Camera mCamera;
    private FFmpegFrameRecorder mFrameRecorder;
    private VideoRecordThread mVideoRecordThread;
    private AudioRecordThread mAudioRecordThread;
    private volatile boolean mRecording = false;
    private File mVideo;
    private LinkedBlockingQueue<FrameToRecord> mFrameToRecordQueue;
    private LinkedBlockingQueue<FrameToRecord> mRecycledFrameQueue;
    private int mFrameToRecordCount;
    private int mFrameRecordedCount;
    private long mTotalProcessFrameTime;
    private Stack<RecordFragment> mRecordFragments;

    private int sampleAudioRateInHz = 44100;
    /* The sides of width and height are based on camera orientation.
    That is, the preview size is the size before it is rotated. */
    private int mPreviewWidth = PREFERRED_PREVIEW_WIDTH;
    private int mPreviewHeight = PREFERRED_PREVIEW_HEIGHT;
    // Output video size
    private int videoWidth = 320;
    private int videoHeight = 240;
    private int frameRate = 30;
    private int frameDepth = Frame.DEPTH_UBYTE;
    private int frameChannels = 2;

    // Workaround for https://code.google.com/p/android/issues/detail?id=190966
    private Runnable doAfterAllPermissionsGranted;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_media_main);
        initViews();
    }

    private void initViews()
    {
        mPreview = findViewById(R.id.camera_preview);
        mBtnResumeOrPause = findViewById(R.id.img_camera_record);
        mBtnDone = findViewById(R.id.img_upload_video);
        mBtnSwitchCamera = findViewById(R.id.img_switch_camera);
        mBtnReset = findViewById(R.id.img_gallary);
        mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
        mBtnSwitchCamera.setImageDrawable(getResources().getDrawable(R.drawable
                .ic_contest_camera_rear));
        //mCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;

        DisplayMetrics displayMetrics = getApplicationContext().getResources().getDisplayMetrics();
        int margin = (int) MiscUtils.dpToPixelConvertor(24, getApplicationContext());
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;
        mPreviewWidth = width;
        mPreviewHeight = height;
        videoWidth = width;
        videoHeight = height;
        setPreviewSize(mPreviewWidth, mPreviewHeight);
        mPreview.setCroppedSizeWeight(width, height);
        mPreview.setSurfaceTextureListener(this);
        mBtnResumeOrPause.setOnClickListener(this);
        mBtnDone.setOnClickListener(this);
        mBtnSwitchCamera.setOnClickListener(this);
        mBtnReset.setOnClickListener(this);

        // At most buffer 10 Frame
        mFrameToRecordQueue = new LinkedBlockingQueue<>(10);
        // At most recycle 2 Frame
        mRecycledFrameQueue = new LinkedBlockingQueue<>(2);
        mRecordFragments = new Stack<>();

        MediaController.configuration(this)
                .setImagesFolderName(Constants.DEFAULT_FOLDER_NAME)
                .setCopyTakenPhotosToPublicGalleryAppFolder(true)
                .setCopyPickedImagesToPublicGalleryAppFolder(true)
                .setCopyPickedVideosToPublicGalleryAppFolder(true)
                .setAllowMultiplePickInGallery(true);

        checkGalleryAppAvailability();
    }

    private void checkGalleryAppAvailability()
    {
        if (!MediaController.canDeviceHandleGallery(this))
        {
            //Device has no app that handles gallery intent
            mBtnReset.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if (doAfterAllPermissionsGranted != null)
        {
            doAfterAllPermissionsGranted.run();
            doAfterAllPermissionsGranted = null;
        }
        else
        {
            String[] neededPermissions = {
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
            };
            List<String> deniedPermissions = new ArrayList<>();
            for (String permission : neededPermissions)
            {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager
                        .PERMISSION_GRANTED)
                {
                    deniedPermissions.add(permission);
                }
            }
            if (deniedPermissions.isEmpty())
            {
                // All permissions are granted
                doAfterAllPermissionsGranted();
            }
            else
            {
                String[] array = new String[deniedPermissions.size()];
                array = deniedPermissions.toArray(array);
                ActivityCompat.requestPermissions(this, array, REQUEST_PERMISSIONS);
            }
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        pauseRecording();
        stopRecording();
        stopPreview();
        releaseCamera();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS)
        {
            boolean permissionsAllGranted = true;
            for (int grantResult : grantResults)
            {
                if (grantResult != PackageManager.PERMISSION_GRANTED)
                {
                    permissionsAllGranted = false;
                    break;
                }
            }
            if (permissionsAllGranted)
            {
                doAfterAllPermissionsGranted = new Runnable()
                {
                    @Override
                    public void run()
                    {
                        doAfterAllPermissionsGranted();
                    }
                };
            }
            else
            {
                doAfterAllPermissionsGranted = new Runnable()
                {
                    @Override
                    public void run()
                    {
                        Toast.makeText(RecordMediaMainActivity.this, R.string
                                .permissions_denied_exit, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                };
            }
        }
    }

    @Override
    public void onSurfaceTextureAvailable(final SurfaceTexture surface, int width, int height)
    {
        startPreview(surface);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height)
    {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface)
    {
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface)
    {
    }

    @Override
    public void onClick(View v)
    {
        int i = v.getId();
        if (i == R.id.img_camera_record) //play pause camera
        {
            if (mRecording)
            {
                pauseRecording();
            }
            else
            {
                resumeRecording();
            }

        }
        else if (i == R.id.img_upload_video)//done record
        {
            //done recordimg code
            pauseRecording();
            // check video length
            if (calculateTotalRecordedTime(mRecordFragments) < MIN_VIDEO_LENGTH)
            {
                Toast.makeText(this, R.string.video_too_short, Toast.LENGTH_SHORT).show();
                return;
            }
            new FinishRecordingTask().execute();

        }
        else if (i == R.id.img_switch_camera)//switch front back camera
        {
            final SurfaceTexture surfaceTexture = mPreview.getSurfaceTexture();
            new ProgressDialogTask<Void, Integer, Void>(R.string.please_wait)
            {

                @Override
                protected Void doInBackground(Void... params)
                {
                    stopRecording();
                    stopPreview();
                    releaseCamera();

                    mCameraId = (mCameraId + 1) % 2;
                    acquireCamera();
                    startPreview(surfaceTexture);
                    startRecording();
                    return null;
                }
            }.execute();

        }
        else if (i == R.id.img_gallary)//reset camera
        {
            MediaController.openGallery(RecordMediaMainActivity.this, 0);
        }
    }

    private void switchCameraImage(int mCameraId)
    {
        if (mCameraId == Camera.CameraInfo.CAMERA_FACING_BACK)
        {
            mBtnSwitchCamera.setImageDrawable(getResources().getDrawable(R.drawable
                    .ic_contest_camera_front));
        }
        else
        {
            mBtnSwitchCamera.setImageDrawable(getResources().getDrawable(R.drawable
                    .ic_contest_camera_rear));
        }
    }

    private void doAfterAllPermissionsGranted()
    {
        acquireCamera();
        switchCameraImage(mCameraId);
        SurfaceTexture surfaceTexture = mPreview.getSurfaceTexture();
        if (surfaceTexture != null)
        {
            // SurfaceTexture already created
            startPreview(surfaceTexture);
        }
        new ProgressDialogTask<Void, Integer, Void>(R.string.initiating)
        {

            @Override
            protected Void doInBackground(Void... params)
            {
                if (mFrameRecorder == null)
                {
                    initRecorder();
                    startRecorder();
                }
                startRecording();
                return null;
            }
        }.execute();
    }

    private void setPreviewSize(int width, int height)
    {
        if (MiscUtils.isOrientationLandscape(this))
        {
            mPreview.setPreviewSize(width, height);
        }
        else
        {
            // Swap width and height
            mPreview.setPreviewSize(height, width);
        }
    }

    private void startPreview(SurfaceTexture surfaceTexture)
    {
        if (mCamera == null)
        {
            return;
        }

        Camera.Parameters parameters = mCamera.getParameters();
        List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();
//        Camera.Size previewSize = CameraHelper.getOptimalSize(previewSizes,
//                PREFERRED_PREVIEW_WIDTH, PREFERRED_PREVIEW_HEIGHT);
        Camera.Size previewSize = CameraHelper.getOptimalSize(previewSizes,
                mPreviewWidth, mPreviewWidth);
        // if changed, reassign values and request layout
        if (mPreviewWidth != previewSize.width || mPreviewHeight != previewSize.height)
        {
            mPreviewWidth = previewSize.width;
            mPreviewHeight = previewSize.height;
            setPreviewSize(mPreviewWidth, mPreviewHeight);
            mPreview.requestLayout();
        }
        parameters.setPreviewSize(mPreviewWidth, mPreviewHeight);
//        parameters.setPreviewFormat(ImageFormat.NV21);
        if (parameters.getSupportedFocusModes().contains(
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
        {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }
        mCamera.setParameters(parameters);

        mCamera.setDisplayOrientation(CameraHelper.getCameraDisplayOrientation(
                this, mCameraId));

        // YCbCr_420_SP (NV21) format
        byte[] bufferByte = new byte[mPreviewWidth * mPreviewHeight * 3 / 2];
        mCamera.addCallbackBuffer(bufferByte);
        mCamera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback()
        {

            private long lastPreviewFrameTime;

            @Override
            public void onPreviewFrame(byte[] data, Camera camera)
            {
                long thisPreviewFrameTime = System.currentTimeMillis();
                if (lastPreviewFrameTime > 0)
                {
                    Log.d(LOG_TAG, "Preview frame interval: " + (thisPreviewFrameTime -
                            lastPreviewFrameTime) + "ms");
                }
                lastPreviewFrameTime = thisPreviewFrameTime;

                // get video data
                if (mRecording)
                {
                    if (mAudioRecordThread == null || !mAudioRecordThread.isRunning())
                    {
                        // wait for AudioRecord to init and start
                        mRecordFragments.peek().setStartTimestamp(System.currentTimeMillis());
                    }
                    else
                    {
                        // pop the current record fragment when calculate total recorded time
                        RecordFragment curFragment = mRecordFragments.pop();
                        long recordedTime = calculateTotalRecordedTime(mRecordFragments);
                        // push it back after calculation
                        mRecordFragments.push(curFragment);
                        long curRecordedTime = System.currentTimeMillis()
                                - curFragment.getStartTimestamp() + recordedTime;
                        // check if exceeds time limit
                        if (curRecordedTime > MAX_VIDEO_LENGTH)
                        {
                            pauseRecording();
                            new FinishRecordingTask().execute();
                            return;
                        }

                        long timestamp = 1000 * curRecordedTime;
                        Frame frame;
                        FrameToRecord frameToRecord = mRecycledFrameQueue.poll();
                        if (frameToRecord != null)
                        {
                            frame = frameToRecord.getFrame();
                            frameToRecord.setTimestamp(timestamp);
                        }
                        else
                        {
                            frame = new Frame(mPreviewWidth, mPreviewHeight, frameDepth,
                                    frameChannels);
                            frameToRecord = new FrameToRecord(timestamp, frame);
                        }
                        ((ByteBuffer) frame.image[0].position(0)).put(data);

                        if (mFrameToRecordQueue.offer(frameToRecord))
                        {
                            mFrameToRecordCount++;
                        }
                    }
                }
                mCamera.addCallbackBuffer(data);
            }
        });

        try
        {
            mCamera.setPreviewTexture(surfaceTexture);
        }
        catch (IOException ioe)
        {
            ioe.printStackTrace();
        }
        mCamera.startPreview();
    }

    private void stopPreview()
    {
        if (mCamera != null)
        {
            mCamera.stopPreview();
            mCamera.setPreviewCallbackWithBuffer(null);
        }
    }

    private void acquireCamera()
    {
        try
        {
            mCamera = Camera.open(mCameraId);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void releaseCamera()
    {
        if (mCamera != null)
        {
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    private void initRecorder()
    {
        Log.i(LOG_TAG, "init mFrameRecorder");

        String recordedTime = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        mVideo = CameraHelper.getOutputMediaFile(recordedTime, CameraHelper.MEDIA_TYPE_VIDEO);
        Log.i(LOG_TAG, "Output Video: " + mVideo);

        mFrameRecorder = new FFmpegFrameRecorder(mVideo, videoWidth, videoHeight, 1);
        mFrameRecorder.setFormat("mp4");
        mFrameRecorder.setSampleRate(sampleAudioRateInHz);
        mFrameRecorder.setFrameRate(frameRate);

        // Use H264
        mFrameRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        // See: https://trac.ffmpeg.org/wiki/Encode/H.264#crf
        /*
         * The range of the quantizer scale is 0-51: where 0 is lossless, 23 is default, and 51
         * is worst possible. A lower value is a higher quality and a subjectively sane range is
         * 18-28. Consider 18 to be visually lossless or nearly so: it should look the same or
         * nearly the same as the input but it isn't technically lossless.
         * The range is exponential, so increasing the CRF value +6 is roughly half the bitrate
         * while -6 is roughly twice the bitrate. General usage is to choose the highest CRF
         * value that still provides an acceptable quality. If the output looks good, then try a
         * higher value and if it looks bad then choose a lower value.
         */
        mFrameRecorder.setVideoOption("crf", "28");
        mFrameRecorder.setVideoOption("preset", "superfast");
        mFrameRecorder.setVideoOption("tune", "zerolatency");
        Log.i(LOG_TAG, "mFrameRecorder initialize success");
    }

    private void releaseRecorder(boolean deleteFile)
    {
        if (mFrameRecorder != null)
        {
            try
            {
                mFrameRecorder.release();
            }
            catch (FFmpegFrameRecorder.Exception e)
            {
                e.printStackTrace();
            }
            mFrameRecorder = null;

            if (deleteFile)
            {
                mVideo.delete();
            }
        }
    }

    private void startRecorder()
    {
        try
        {
            if (mFrameRecorder != null)
            {
                mFrameRecorder.start();
            }
        }
        catch (FFmpegFrameRecorder.Exception e)
        {
            e.printStackTrace();
        }
    }

    private void stopRecorder()
    {
        if (mFrameRecorder != null)
        {
            try
            {
                mFrameRecorder.stop();
            }
            catch (FFmpegFrameRecorder.Exception e)
            {
                e.printStackTrace();
            }
        }
        mRecordFragments.clear();
    }

    private void startRecording()
    {
        mAudioRecordThread = new AudioRecordThread();
        mAudioRecordThread.start();
        mVideoRecordThread = new VideoRecordThread();
        mVideoRecordThread.start();
    }

    private void stopRecording()
    {
        if (mAudioRecordThread != null)
        {
            if (mAudioRecordThread.isRunning())
            {
                mAudioRecordThread.stopRunning();
            }
        }

        if (mVideoRecordThread != null)
        {
            if (mVideoRecordThread.isRunning())
            {
                mVideoRecordThread.stopRunning();
            }
        }

        try
        {
            if (mAudioRecordThread != null)
            {
                mAudioRecordThread.join();
            }
            if (mVideoRecordThread != null)
            {
                mVideoRecordThread.join();
            }
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        mAudioRecordThread = null;
        mVideoRecordThread = null;


        mFrameToRecordQueue.clear();
        mRecycledFrameQueue.clear();
    }

    private void resumeRecording()
    {
        if (!mRecording)
        {
            RecordFragment recordFragment = new RecordFragment();
            recordFragment.setStartTimestamp(System.currentTimeMillis());
            mRecordFragments.push(recordFragment);
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    mBtnSwitchCamera.setVisibility(View.INVISIBLE);
                    mBtnResumeOrPause.setImageResource(android.R.drawable.ic_menu_edit);
                }
            });
            mRecording = true;
        }
    }

    private void pauseRecording()
    {
        if (mRecording)
        {
            mRecordFragments.peek().setEndTimestamp(System.currentTimeMillis());
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    mBtnSwitchCamera.setVisibility(View.VISIBLE);
                    mBtnResumeOrPause.setImageResource(android.R.drawable.ic_menu_camera);
                }
            });
            mRecording = false;
        }
    }

    private long calculateTotalRecordedTime(Stack<RecordFragment> recordFragments)
    {
        long recordedTime = 0;
        for (RecordFragment recordFragment : recordFragments)
        {
            recordedTime += recordFragment.getDuration();
        }
        return recordedTime;
    }

    class RunningThread extends Thread
    {
        boolean isRunning;

        public boolean isRunning()
        {
            return isRunning;
        }

        public void stopRunning()
        {
            this.isRunning = false;
        }
    }

    class AudioRecordThread extends RunningThread
    {
        private AudioRecord mAudioRecord;
        private ShortBuffer audioData;

        public AudioRecordThread()
        {
            int bufferSize = AudioRecord.getMinBufferSize(sampleAudioRateInHz,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleAudioRateInHz,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

            audioData = ShortBuffer.allocate(bufferSize);
        }

        @Override
        public void run()
        {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

            Log.d(LOG_TAG, "mAudioRecord startRecording");
            mAudioRecord.startRecording();

            isRunning = true;
            /* ffmpeg_audio encoding loop */
            while (isRunning)
            {
                if (mRecording && mFrameRecorder != null)
                {
                    int bufferReadResult = mAudioRecord.read(audioData.array(), 0, audioData
                            .capacity());
                    audioData.limit(bufferReadResult);
                    if (bufferReadResult > 0)
                    {
                        Log.v(LOG_TAG, "bufferReadResult: " + bufferReadResult);
                        try
                        {
                            if (mFrameRecorder != null)
                            {
                                mFrameRecorder.recordSamples(audioData);
                            }
                        }
                        catch (FFmpegFrameRecorder.Exception e)
                        {
                            Log.v(LOG_TAG, e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            }
            Log.d(LOG_TAG, "mAudioRecord stopRecording");
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
            Log.d(LOG_TAG, "mAudioRecord released");
        }
    }

    class VideoRecordThread extends RunningThread
    {
        @Override
        public void run()
        {
            int previewWidth = mPreviewWidth;
            int previewHeight = mPreviewHeight;

            List<String> filters = new ArrayList<>();
            // Transpose
            String transpose = null;
            String hflip = null;
            String vflip = null;
            String crop = null;
            String scale = null;
            int cropWidth;
            int cropHeight;
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(mCameraId, info);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            switch (rotation)
            {
                case Surface.ROTATION_0:
                    switch (info.orientation)
                    {
                        case 270:
                            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
                            {
                                transpose = "transpose=clock_flip"; // Same as preview display
                            }
                            else
                            {
                                transpose = "transpose=cclock"; // Mirrored horizontally as
                                // preview display
                            }
                            break;
                        case 90:
                            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
                            {
                                transpose = "transpose=cclock_flip"; // Same as preview display
                            }
                            else
                            {
                                transpose = "transpose=clock"; // Mirrored horizontally as
                                // preview display
                            }
                            break;
                    }
                    cropWidth = previewHeight;
                    cropHeight = cropWidth * videoHeight / videoWidth;
                    crop = String.format("crop=%d:%d:%d:%d",
                            cropWidth, cropHeight,
                            (previewHeight - cropWidth) / 2, (previewWidth - cropHeight) / 2);
                    // swap width and height
                    scale = String.format("scale=%d:%d", videoHeight, videoWidth);
                    break;
                case Surface.ROTATION_90:
                case Surface.ROTATION_270:
                    switch (rotation)
                    {
                        case Surface.ROTATION_90:
                            // landscape-left
                            switch (info.orientation)
                            {
                                case 270:
                                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
                                    {
                                        hflip = "hflip";
                                    }
                                    break;
                            }
                            break;
                        case Surface.ROTATION_270:
                            // landscape-right
                            switch (info.orientation)
                            {
                                case 90:
                                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK)
                                    {
                                        hflip = "hflip";
                                        vflip = "vflip";
                                    }
                                    break;
                                case 270:
                                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
                                    {
                                        vflip = "vflip";
                                    }
                                    break;
                            }
                            break;
                    }
                    cropHeight = previewHeight;
                    cropWidth = cropHeight * videoWidth / videoHeight;
                    crop = String.format("crop=%d:%d:%d:%d",
                            cropWidth, cropHeight,
                            (previewWidth - cropWidth) / 2, (previewHeight - cropHeight) / 2);
                    scale = String.format("scale=%d:%d", videoWidth, videoHeight);
                    break;
                case Surface.ROTATION_180:
                    break;
            }
            // transpose
            if (transpose != null)
            {
                filters.add(transpose);
            }
            // horizontal flip
            if (hflip != null)
            {
                filters.add(hflip);
            }
            // vertical flip
            if (vflip != null)
            {
                filters.add(vflip);
            }
            // crop
            if (crop != null)
            {
                filters.add(crop);
            }
            // scale (to designated size)
            if (scale != null)
            {
                filters.add(scale);
            }

            FFmpegFrameFilter frameFilter = new FFmpegFrameFilter(TextUtils.join(",", filters),
                    previewWidth, previewHeight);
            frameFilter.setPixelFormat(avutil.AV_PIX_FMT_NV21);
            frameFilter.setFrameRate(frameRate);
            try
            {
                frameFilter.start();
            }
            catch (FrameFilter.Exception e)
            {
                e.printStackTrace();
            }

            isRunning = true;
            FrameToRecord recordedFrame;

            while (isRunning || !mFrameToRecordQueue.isEmpty())
            {
                try
                {
                    recordedFrame = mFrameToRecordQueue.take();
                }
                catch (InterruptedException ie)
                {
                    ie.printStackTrace();
                    try
                    {
                        frameFilter.stop();
                    }
                    catch (FrameFilter.Exception e)
                    {
                        e.printStackTrace();
                    }
                    break;
                }

                if (mFrameRecorder != null)
                {
                    long timestamp = recordedFrame.getTimestamp();
                    if (timestamp > mFrameRecorder.getTimestamp())
                    {
                        mFrameRecorder.setTimestamp(timestamp);
                    }
                    long startTime = System.currentTimeMillis();
//                    Frame filteredFrame = recordedFrame.getFrame();
                    Frame filteredFrame = null;
                    try
                    {
                        frameFilter.push(recordedFrame.getFrame());
                        filteredFrame = frameFilter.pull();
                    }
                    catch (FrameFilter.Exception e)
                    {
                        e.printStackTrace();
                    }
                    try
                    {
                        mFrameRecorder.record(filteredFrame);
                    }
                    catch (FFmpegFrameRecorder.Exception e)
                    {
                        e.printStackTrace();
                    }
                    long endTime = System.currentTimeMillis();
                    long processTime = endTime - startTime;
                    mTotalProcessFrameTime += processTime;
                    Log.d(LOG_TAG, "This frame process time: " + processTime + "ms");
                    long totalAvg = mTotalProcessFrameTime / ++mFrameRecordedCount;
                    Log.d(LOG_TAG, "Avg frame process time: " + totalAvg + "ms");
                }
                Log.d(LOG_TAG, mFrameRecordedCount + " / " + mFrameToRecordCount);
                mRecycledFrameQueue.offer(recordedFrame);
            }
        }

        public void stopRunning()
        {
            super.stopRunning();
            if (getState() == WAITING)
            {
                interrupt();
            }
        }
    }

    abstract class ProgressDialogTask<Params, Progress, Result> extends AsyncTask<Params,
            Progress, Result>
    {

        private int promptRes;
        private ProgressDialog mProgressDialog;

        public ProgressDialogTask(int promptRes)
        {
            this.promptRes = promptRes;
        }

        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();
            mProgressDialog = ProgressDialog.show(RecordMediaMainActivity.this,
                    null, getString(promptRes), true);
        }

        @Override
        protected void onProgressUpdate(Progress... values)
        {
            super.onProgressUpdate(values);
//            mProgressDialog.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(Result result)
        {
            super.onPostExecute(result);
            mProgressDialog.dismiss();
            switchCameraImage(mCameraId);
        }
    }

    class FinishRecordingTask extends ProgressDialogTask<Void, Integer, Void>
    {

        public FinishRecordingTask()
        {
            super(R.string.processing);
        }

        @Override
        protected Void doInBackground(Void... params)
        {
            stopRecording();
            stopRecorder();
            releaseRecorder(false);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid)
        {
            super.onPostExecute(aVoid);
            if (mVideo != null && mVideo.getPath() != null)
            {

                Intent intent = new Intent(RecordMediaMainActivity.this, PlaybackActivity.class);
                intent.putExtra(PlaybackActivity.INTENT_NAME_VIDEO_PATH, mVideo.getPath());
                startActivity(intent);
            }
            else
            {
                Log.d(LOG_TAG, "onPostExecute: video path blank");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        MediaController.handleActivityResult(requestCode, resultCode, data, this, new
                DefaultCallback()
                {
                    @Override
                    public void onImagePickerError(Exception e, MediaController.ImageSource
                            source, int
                                                           type)
                    {
                        //Some error handling
                        e.printStackTrace();
                    }

                    @Override
                    public void onImagesPicked(List<File> imageFiles, MediaController.ImageSource
                            source,
                                               int
                                                       type)
                    {
                        onPhotosReturned(imageFiles);
                    }

                    @Override
                    public void onCanceled(MediaController.ImageSource source, int type)
                    {
                        //Cancel handling, you might wanna remove taken photo if it was canceled
                        if (source == MediaController.ImageSource.CAMERA_IMAGE)
                        {
                            File photoFile = MediaController.lastlyTakenButCanceledPhoto
                                    (RecordMediaMainActivity
                                            .this);
                            if (photoFile != null)
                            {
                                photoFile.delete();
                            }
                        }
                    }
                });
    }

    private void onPhotosReturned(List<File> returnedPhotos)
    {
        Log.d(LOG_TAG, "onPhotosReturned: " + returnedPhotos.toString());
        Log.d(LOG_TAG, "onPhotosReturned: Saved Path " + MediaController.getMediaPath());
        if (returnedPhotos.size() > 0 && returnedPhotos.get(0) != null)
        {
            try
            {
                Log.d(LOG_TAG, "onPhotosReturned:  path " + returnedPhotos.get(0).getPath()
                        + "\n absolutepath" + returnedPhotos.get(0).getAbsolutePath()
                        + "\n canonicalpath" + returnedPhotos.get(0).getCanonicalPath()
                        + "\n parent file" + returnedPhotos.get(0).getParentFile());
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            Intent intent = new Intent(RecordMediaMainActivity.this, PlaybackActivity.class);
            intent.putExtra(PlaybackActivity.INTENT_NAME_VIDEO_PATH, returnedPhotos.get(0)
                    .getAbsolutePath());
            startActivity(intent);
        }
        else
        {
            Log.d(LOG_TAG, "onPostExecute: video path blank");
        }
    }

    @Override
    protected void onDestroy()
    {
        // Clear any configuration that was done!
        MediaController.clearConfiguration(this);
        super.onDestroy();
        stopRecorder();
        releaseRecorder(true);
    }

    @Override
    public void onBackPressed()
    {
        //super.onBackPressed();
        if (mRecording)
        {
            pauseRecording();
        }
        MediaController.clearConfiguration(this);
        stopRecorder();
        releaseRecorder(true);
    }
}