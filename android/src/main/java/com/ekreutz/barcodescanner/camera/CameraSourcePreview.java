/*
 * Copyright (C) The Android Open Source Project
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
package com.ekreutz.barcodescanner.camera;

import android.Manifest;
import android.content.Context;
import android.content.res.Configuration;
import android.support.annotation.RequiresPermission;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import com.google.android.gms.common.images.Size;
import com.google.android.gms.vision.Detector;

import java.io.IOException;

public class CameraSourcePreview extends ViewGroup {
    private static final String TAG = "CameraSourcePreview";

    private Context mContext;
    private SurfaceView mSurfaceView;
    private boolean mStartRequested;
    private boolean mSurfaceAvailable;
    private CameraSource mCameraSource;

    public CameraSourcePreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mStartRequested = false;
        mSurfaceAvailable = false;

        mSurfaceView = new SurfaceView(context);
        mSurfaceView.getHolder().addCallback(new SurfaceCallback());
        addView(mSurfaceView);
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    public void start(CameraSource cameraSource) throws IOException, SecurityException {
        if (cameraSource == null) {
            stop();
        }

        mCameraSource = cameraSource;

        if (mCameraSource != null) {
            mStartRequested = true;
            startIfReady();
        }
    }

    public void stop() {
        if (mCameraSource != null) {
            mCameraSource.stop();

        }
    }

    public void release() {
        if (mCameraSource != null) {
            mCameraSource.release();
            mCameraSource = null;
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    private void startIfReady() throws IOException, SecurityException {
        if (mStartRequested && mSurfaceAvailable && mCameraSource != null) {
            mCameraSource.start(mSurfaceView.getHolder());
            mStartRequested = false;
        }
    }

    // Can be quite heavy, since it stops and restarts the camera
    @RequiresPermission(Manifest.permission.CAMERA)
    public void replaceBarcodeDetector(Detector<?> detector, boolean shouldResume) throws IOException, SecurityException {
        if (mCameraSource != null) {
            mCameraSource.release();
            mCameraSource.setDetector(detector);

            if (shouldResume && mSurfaceAvailable) {
                start(mCameraSource);
            }
        }
    }

    private class SurfaceCallback implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder surface) {
            mSurfaceAvailable = true;
            Log.d("BARCODETYPE", "surface created");
            Log.d("LAYOUTING", "surface created");
            try {
                startIfReady();
            } catch (SecurityException se) {
                Log.e(TAG,"Do not have permission to start the camera", se);
            } catch (IOException e) {
                Log.e(TAG, "Could not start camera source.", e);
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surface) {
            Log.d("LAYOUTING", "destroyed");
            mSurfaceAvailable = false;
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.d("BARCODETYPE", "surface changed");
            Log.d("LAYOUTING", String.format("surface changed %d x %d", width, height));
            previewLayout();
        }
    }

    private int mWidth = 0, mHeight = 0;

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        mWidth = right - left;
        mHeight = bottom - top;

        Log.d("LAYOUTING", String.format("onLayout %d x %d", mWidth, mHeight));

        mCameraSource.setRotation();
        previewLayout();
    }

    private void previewLayout() {
        if (mWidth == 0 || mHeight == 0) return;

        int previewWidth = 800;
        int previewHeight = 480;

        if (mCameraSource != null) {
            Size size = mCameraSource.getPreviewSize();
            if (size != null) {
                previewWidth = size.getWidth();
                previewHeight = size.getHeight();
            }
        }

        // Swap width and height sizes when in portrait, since it will be rotated 90 degrees
        if (isPortraitMode()) {
            Log.d("LAYOUTING", "portrait");
            int tmp = previewWidth;
            previewWidth = previewHeight;
            previewHeight = tmp;
        } else {
            Log.d("LAYOUTING", "landscape");
        }

        fillLayout(mWidth, mHeight, previewWidth, previewHeight);

        try {
            startIfReady();
        } catch (SecurityException se) {
            Log.e(TAG,"Do not have permission to start the camera", se);
        } catch (IOException e) {
            Log.e(TAG, "Could not start camera source.", e);
        }
    }

    /* Fit the child inside of this preview */
    private void fitLayout(final int layoutWidth, final int layoutHeight, int width, int height) {
        // Computes height and width for potentially doing fit width.
        int childWidth = layoutWidth;
        int childHeight = (int)(((float) layoutWidth / (float) width) * height);

        // If height is too tall using fit width, does fit height instead.
        if (childHeight > layoutHeight) {
            childHeight = layoutHeight;
            childWidth = (int)(((float) layoutHeight / (float) height) * width);
        }

        for (int i = 0; i < getChildCount(); ++i) {
            getChildAt(i).layout(0, 0, childWidth, childHeight);
        }
    }

    /* Make the child fill the whole preview area, at the cost of a little cropping (possibly) */
    private void fillLayout(final int layoutWidth, final int layoutHeight, int width, int height) {
        int childWidth;
        int childHeight;
        int xPadding = 0;
        int yPadding = 0;
        float widthRatio = (float) layoutWidth / (float) width;
        float heightRatio = (float) layoutHeight / (float) height;

        // To fill the view with the camera preview, while also preserving the correct aspect ratio,
        // it is usually necessary to slightly oversize the child and to crop off portions along one
        // of the dimensions.  We scale up based on the dimension requiring the most correction, and
        // compute a crop offset for the other dimension.
        if (widthRatio > heightRatio) {
            childWidth = layoutWidth;
            childHeight = (int) ((float) height * widthRatio);
            yPadding = (childHeight - layoutHeight) / 2;
        } else {
            childWidth = (int) ((float) width * heightRatio);
            childHeight = layoutHeight;
            xPadding = (childWidth - layoutWidth) / 2;
        }

        float paddingArea = (float) xPadding * 2 * childHeight + yPadding * 2 * childWidth;
        float childArea = (float) childHeight * childWidth;


        Log.d("LAYOUTING", String.format("Layout: %d%% of preview was cropped.", (int)(paddingArea / childArea * 100)));

        for (int i = 0; i < getChildCount(); ++i) {
            getChildAt(i).layout(-xPadding, -yPadding, childWidth - xPadding, childHeight - yPadding);
        }
    }

    private boolean isPortraitMode() {
        int orientation = mContext.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return false;
        }
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            return true;
        }

        Log.d(TAG, "isPortraitMode returning false by default");
        return false;
    }
}
