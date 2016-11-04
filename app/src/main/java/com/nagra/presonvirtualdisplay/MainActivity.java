package com.nagra.presonvirtualdisplay;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.andronblog.presentationonvirtualdisplay.DemoPresentation;
import com.andronblog.presentationonvirtualdisplay.R;
import com.andronblog.presentationonvirtualdisplay.RecorderHelper;

import java.io.IOException;

public class MainActivity extends Activity
{
    private static final String TAG = "MainActivity";

    private static final int INVALID_DISPLAY_ID = -1;
    private static final int SCREEN_CAPTURE_PERMISSION_CODE = 1;
    private static final int EXTERNAL_STORAGE_PERMISSION_CODE = 2;

    private static final int FRAMERATE = 30;
    private static final String FILENAME = Environment.getExternalStorageDirectory().getPath()+"/presentation.mp4";

    private int mWidth;
    private int mHeight;
    private DisplayMetrics mMetrics = new DisplayMetrics();

    private DisplayManager mDisplayManager;
    private VirtualDisplay mVirtualDisplay;
    private MediaRecorder mMediaRecorder;

    private int mResultCode;
    private Intent mResultData;

    private MediaProjectionManager mProjectionManager;
    private MediaProjection mProjection;
    private MediaProjection.Callback mProjectionCallback;

    private MediaPlayer mMediaPlayer;
    private SurfaceView mSurfaceView;

    private Surface mSurface;
    private Button mButtonCreate;
    private Button mButtonDestroy;
    private Button mButtonPlayVideo;
    private Button mButtonStopVideo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "Main activity creation");

        setContentView(R.layout.activity_main);

        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        mSurface = mSurfaceView.getHolder().getSurface();

        // Obtain display metrics of current display to know its density (dpi)
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        display.getMetrics(mMetrics);

        // Initialize resolution of virtual display in pixels to show
        // the surface view on full screen
        mWidth = mSurfaceView.getLayoutParams().width;
        mHeight = mSurfaceView.getLayoutParams().height;
        Log.i(TAG, "Surface view size: (" + mWidth + "x" + mHeight + ")");

        mDisplayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        mDisplayManager.registerDisplayListener(mDisplayListener, null);
        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mMediaRecorder = new MediaRecorder();

        mButtonCreate = (Button) findViewById(R.id.btn_create_virtual_display);
        mButtonCreate.setEnabled(false);
        mButtonCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startScreenCapture();
            }
        });

        mButtonDestroy = (Button) findViewById(R.id.btn_destroy_virtual_display);
        mButtonDestroy.setEnabled(false);
        mButtonDestroy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopScreenCapture();
            }
        });

        mButtonPlayVideo = (Button) findViewById(R.id.btn_play);
        mButtonPlayVideo.setEnabled(false);
        mButtonPlayVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mMediaPlayer == null) {
                    Uri uri = Uri.parse(FILENAME);
                    mMediaPlayer = MediaPlayer.create(MainActivity.this, uri, mSurfaceView.getHolder());
                } else {
                    try {
                        mMediaPlayer.prepare();
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                }
                mMediaPlayer.start();
                mButtonCreate.setEnabled(false);
                mButtonDestroy.setEnabled(false);
                mButtonPlayVideo.setEnabled(false);
                mButtonStopVideo.setEnabled(true);
            }
        });

        mButtonStopVideo = (Button) findViewById(R.id.btn_stop);
        mButtonStopVideo.setEnabled(false);
        mButtonStopVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMediaPlayer.stop();
                mButtonCreate.setEnabled(true);
                mButtonDestroy.setEnabled(false);
                mButtonPlayVideo.setEnabled(true);
                mButtonStopVideo.setEnabled(false);
            }
        });

        // Check if we have write permission
        int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Write permissions is not granted");
            // Request permissions
            ActivityCompat.requestPermissions(this,
                    new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    EXTERNAL_STORAGE_PERMISSION_CODE);
        } else {
            Log.i(TAG, "Write permission is granted!");
            mButtonCreate.setEnabled(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch(requestCode) {
            case EXTERNAL_STORAGE_PERMISSION_CODE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Write permission is granted!");
                    mButtonCreate.setEnabled(true);
                } else {
                    Toast.makeText(this, "Write permission is not granted", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        destroyVirtualDisplay();
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
            mButtonPlayVideo.setEnabled(false);
            mButtonStopVideo.setEnabled(false);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        mDisplayManager.unregisterDisplayListener(mDisplayListener);
        if (mProjection != null) {
            Log.i(TAG, "Stop media projection");
            mProjection.unregisterCallback(mProjectionCallback);
            mProjection.stop();
            mProjection = null;
        }
        mMediaRecorder.release();
    }

    private void startScreenCapture() {
        if (mProjection != null) {
            // start virtual display
            Log.i(TAG, "The media projection is already gotten");
            createVirtualDisplay();
        } else if (mResultCode != 0 && mResultData != null) {
            // get media projection
            Log.i(TAG, "Get media projection with the existing permission");
            mProjection = getProjection();
            createVirtualDisplay();
        } else {
            Log.i(TAG, "Request the permission for media projection");
            startActivityForResult(mProjectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_PERMISSION_CODE);
        }
    }

    private void stopScreenCapture() {
        destroyVirtualDisplay();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        mResultCode = resultCode;
        mResultData = data;
        if (requestCode != SCREEN_CAPTURE_PERMISSION_CODE) {
            Toast.makeText(this, "Unknown request code: " + requestCode, Toast.LENGTH_SHORT).show();
            return;
        }
        if (resultCode != RESULT_OK) {
            Toast.makeText(this, "Screen Cast Permission Denied", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.i(TAG, "Get media projection with the new permission");
        mProjection = getProjection();
        createVirtualDisplay();
    }

    private MediaProjection getProjection() {
        MediaProjection projection = mProjectionManager.getMediaProjection(mResultCode, mResultData);
        // Add a callback to be informed if the projection
        // will be stopped from the status bar.
        mProjectionCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                Log.d(TAG, "MediaProjection.Callback onStop obj:" + toString());
                destroyVirtualDisplay();
                mProjection = null;
            }
        };
        projection.registerCallback(mProjectionCallback, null);
        return projection;
    }

    private void createVirtualDisplay() {
        if (mProjection != null && mVirtualDisplay == null) {
            Log.d(TAG, "createVirtualDisplay WxH (px): " + mWidth + "x" + mHeight +
                    ", dpi: " + mMetrics.densityDpi);
            if (!prepareMediaRecorder(mWidth, mHeight, FRAMERATE, FILENAME)) {
                Toast.makeText(this, "Can't prepare MediaRecorder", Toast.LENGTH_LONG).show();
                return;
            }
            int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
            //flags |= DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
            mVirtualDisplay = mProjection.createVirtualDisplay("MyVirtualDisplay",
                    mWidth, mHeight, mMetrics.densityDpi, flags, mMediaRecorder.getSurface(),
                    null /*Callbacks*/, null /*Handler*/);
            mButtonCreate.setEnabled(false);
            mButtonDestroy.setEnabled(true);
            mButtonPlayVideo.setEnabled(false);
            // Release the previous instance of media player before recording new data into the same file.
            if (mMediaPlayer != null) {
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
            // Start recording the content of MediaRecorder surface rendering by VirtualDisplay
            // into file.
            mMediaRecorder.start();
        }
    }

    private void destroyVirtualDisplay() {
        Log.d(TAG, "destroyVirtualDisplay");
        if (mVirtualDisplay != null) {
            Log.d(TAG, "destroyVirtualDisplay release");
            mVirtualDisplay.release();
            mVirtualDisplay = null;
            mMediaRecorder.stop();
        }
        mButtonDestroy.setEnabled(false);
        mButtonCreate.setEnabled(true);
        mButtonPlayVideo.setEnabled(true);
    }

    private boolean prepareMediaRecorder(int width, int height, int framerate, String filename) {
        Size sz = new Size(width, height);
        boolean supported = RecorderHelper.isSupportedByAVCEncoder(sz, framerate);
        if (!supported) {
            Log.e(TAG, "The combination of video size and framerate is not supported by MediaCodec");
            return false;
        }

        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setVideoEncodingBitRate(RecorderHelper.getVideoBitRate(sz));
        mMediaRecorder.setVideoFrameRate(framerate);
        mMediaRecorder.setVideoSize(sz.getWidth(), sz.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setOutputFile(filename);
        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Prepare MediaRecorder is failed");
            return false;
        }

        return true;
    }

    private final DisplayManager.DisplayListener mDisplayListener = new DisplayManager.DisplayListener() {

        private boolean mNewDisplayAdded = false;
        private int mCurrentDisplayId = INVALID_DISPLAY_ID;
        private DemoPresentation mPresentation;

        @Override
        public void onDisplayAdded(int displayId) {
            Log.d(TAG, "==> onDisplayAdded id=" + displayId);
            if (!mNewDisplayAdded && mCurrentDisplayId == INVALID_DISPLAY_ID) {
                mNewDisplayAdded = true;
                mCurrentDisplayId = displayId;
            }
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            Log.d(TAG, "==> onDisplayRemoved id=" + displayId);
            if (mCurrentDisplayId == displayId) {
                mNewDisplayAdded = false;
                mCurrentDisplayId = INVALID_DISPLAY_ID;
                if (mPresentation != null) {
                    mPresentation.dismiss();
                    mPresentation = null;
                }
            }
        }

        @Override
        public void onDisplayChanged(int displayId) {
            Log.d(TAG, "==> onDisplayChanged id=" + displayId);
            if (mCurrentDisplayId == displayId) {
                if (mNewDisplayAdded) {
                    // create a presentation
                    mNewDisplayAdded = false;
                    Display display = mDisplayManager.getDisplay(displayId);
                    mPresentation = new DemoPresentation(MainActivity.this, display);
                    mPresentation.show();
                }
            }
        }
    };
}
