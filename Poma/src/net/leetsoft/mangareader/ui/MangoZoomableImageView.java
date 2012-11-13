package net.leetsoft.mangareader.ui;

/*
 * Copyright 2012 Laurence Dawson
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

/*
 * This class is based upon the file ImageViewTouchBase.java which can be found at:
 * https://dl-ssl.google.com/dl/googlesource/git-repo/repo
 *  
 * Copyright (C) 2009 The Android Open Source Project
 */

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Build;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.*;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import net.leetsoft.mangareader.Mango;
import net.leetsoft.compatibility.ScaleGestureDetectorCompat;

public class MangoZoomableImageView extends View
{

    // Statics
    static final int PAN_ANIMATION_DURATION = 500;
    static final int ZOOM_ANIMATION_DURATION = 500;
    static final float sPanRate = 7;
    static final float sScaleRate = 1.25F;
    static final int sPaintDelay = 200;
    //static final int                   sAnimationDelay         = 500;

    // This is the base transformation which is used to show the image
    // initially. The current computation for this shows the image in
    // it's entirety, letterboxing as needed. One could chose to
    // show the image as cropped instead.
    //
    // This matrix is recomputed when we go from the thumbnail image to
    // the full size image.
    private Matrix mBaseMatrix = new Matrix();

    // This is the supplementary transformation which reflects what
    // the user has done in terms of zooming and panning.
    //
    // This matrix remains the same when we go from the thumbnail image
    // to the full size image.
    private Matrix mSuppMatrix = new Matrix();

    // This is the final matrix which is computed as the concatentation
    // of the base matrix and the supplementary matrix.
    private Matrix mDisplayMatrix = new Matrix();

    // A replacement ImageView matrix
    private Matrix mMatrix = new Matrix();

    // Used to filter the bitmaps when hardware acceleration is not enabled
    private Paint mPaint;

    // Temporary buffer used for getting the values out of a matrix.
    private float[] mMatrixValues = new float[9];

    // The current bitmap being displayed.
    private Bitmap mBitmap;

    // Dimensions for the view
    private int mThisWidth = -1, mThisHeight = -1;

    // The max zoom for the view, determined programatically
    private float mMaxZoom;
    private float mMinZoom;

    // If not null, calls setImageBitmap when onLayout is triggered
    private Runnable mOnLayoutRunnable = null;

    // Stacked to the internal queue to invalidate the view
    private Runnable mRefresh = null;

    // Stacked to the internal queue to scroll the view
    private Runnable mFling = null;

    // The time of the last draw operation
    private double mLastDraw = 0;

    // Scale and gesture listeners for the view
    private ScaleGestureDetectorCompat mScaleDetector;
    private GestureDetector mGestureDetector;

    // Leetsoft listeners
    private Runnable mTapCallback;
    private Runnable mBackTapCallback;
    private Runnable mLeftFlingCallback;
    private Runnable mRightFlingCallback;

    // Prevent tap event from firing after quick pinch zooms
    private long mInvalidateTapTimer;

    // Longpress zoom
    private boolean mLongPressActive;
    private float mLongPressLastY;
    private float mLongPressCenterX;
    private float mLongPressCenterY;
    private float mLongPressScale;

    // HQ rendering
    private boolean mEnableFastRendering;
    private boolean mForceSoftwareRendering;


    // Programatic entry point
    public MangoZoomableImageView(Context context)
    {
        super(context);
        init(context);
    }

    // XML entry point
    public MangoZoomableImageView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        init(context);
    }

    // Setup the view
    private void init(Context context)
    {
        mPaint = new Paint();
        mPaint.setDither(true);
        mPaint.setFilterBitmap(true);
        mPaint.setAntiAlias(true);

        mEnableFastRendering = Mango.getSharedPreferences().getBoolean("useFastRendering", false);
        mForceSoftwareRendering = Mango.getSharedPreferences().getBoolean("forceSoftwareRendering", false);

        // Setup the refresh runnable
        mRefresh = new Runnable()
        {
            @Override
            public void run()
            {
                postInvalidate();
            }
        };

        // Setup the gesture and scale listeners
        mScaleDetector = new ScaleGestureDetectorCompat(context, new ScaleListener());
        mGestureDetector = new GestureDetector(context, new TouchGestureListener());

        // Force hardware acceleration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
        {
            if (mForceSoftwareRendering)
                setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            else
                setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
    }

    // Get the bitmap for the view
    public Bitmap getImageBitmap()
    {
        return mBitmap;
    }

    // Free the bitmaps and matrices
    public void clear()
    {
        if (mBitmap != null)
            mBitmap = null;
    }

    // When the layout is calculated, set the
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom)
    {
        super.onLayout(changed, left, top, right, bottom);
        mThisWidth = right - left;
        mThisHeight = bottom - top;
        Runnable r = mOnLayoutRunnable;
        if (r != null)
        {
            mOnLayoutRunnable = null;
            r.run();
        }
        if (mBitmap != null)
        {
            setBaseMatrix(mBitmap, mBaseMatrix);
            setImageMatrix(getImageViewMatrix());
        }
    }

    // Translate a given point through a given matrix.
    static private void translatePoint(Matrix matrix, float[] xy)
    {
        matrix.mapPoints(xy);
    }

    // Identical to the setImageMatrix method in ImageView
    public void setImageMatrix(Matrix m)
    {
        if (m != null && m.isIdentity())
        {
            m = null;
        }

        // don't invalidate unless we're actually changing our matrix
        if (m == null && !this.mMatrix.isIdentity() || m != null && !this.mMatrix.equals(m))
        {
            this.mMatrix.set(m);
            invalidate();
        }
    }

    // Sets the bitmap for the image and resets the base
    public void setImageBitmap(final Bitmap bitmap)
    {
        final int viewWidth = getWidth();

        // Mango.Log("Imageviewer", "setImageBitmap: " + String.valueOf(bitmap));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && bitmap != null && bitmap.getHeight() > 1800)
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        if (viewWidth <= 0)
        {
            mOnLayoutRunnable = new Runnable()
            {
                @Override
                public void run()
                {
                    setImageBitmap(bitmap);
                }
            };
            return;
        }

        if (bitmap != null)
        {


            setBaseMatrix(bitmap, mBaseMatrix);
            if (Mango.getSharedPreferences().getBoolean("invertManga", false))
                this.mBitmap = Mango.invertBitmap(bitmap);
            else
                this.mBitmap = bitmap;
        }
        else
        {
            mBaseMatrix.reset();
            this.mBitmap = bitmap;
        }

        mSuppMatrix.reset();
        setImageMatrix(getImageViewMatrix());
        mMaxZoom = maxZoom();

        // Set the image to fit the screen
        zoomToDefault(false);

        invalidate();
    }

    // Unchanged from ImageViewTouchBase
    // Center as much as possible in one or both axis. Centering is
    // defined as follows: if the image is scaled down below the
    // view's dimensions then center it (literally). If the image
    // is scaled larger than the view and is translated out of view
    // then translate it back into view (i.e. eliminate black bars).
    protected void center(boolean vertical, boolean horizontal, boolean animate)
    {
        if (mBitmap == null)
            return;

        Matrix m = getImageViewMatrix();

        float[] topLeft = new float[]{0,
                0};
        float[] botRight = new float[]{mBitmap.getWidth(),
                mBitmap.getHeight()};

        translatePoint(m, topLeft);
        translatePoint(m, botRight);

        float height = botRight[1] - topLeft[1];
        float width = botRight[0] - topLeft[0];

        float deltaX = 0, deltaY = 0;

        if (vertical)
        {
            int viewHeight = getHeight();
            if (height < viewHeight)
            {
                deltaY = (viewHeight - height) / 2 - topLeft[1];
            }
            else if (topLeft[1] > 0)
            {
                deltaY = -topLeft[1];
            }
            else if (botRight[1] < viewHeight)
            {
                deltaY = getHeight() - botRight[1];
            }
        }

        if (horizontal)
        {
            int viewWidth = getWidth();
            if (width < viewWidth)
            {
                deltaX = (viewWidth - width) / 2 - topLeft[0];
            }
            else if (topLeft[0] > 0)
            {
                deltaX = -topLeft[0];
            }
            else if (botRight[0] < viewWidth)
            {
                deltaX = viewWidth - botRight[0];
            }
        }

        postTranslate(deltaX, deltaY);
        if (animate)
        {
            Animation a = new TranslateAnimation(-deltaX, 0, -deltaY, 0);
            a.setStartTime(SystemClock.elapsedRealtime());
            a.setDuration(250);
            setAnimation(a);
        }
        setImageMatrix(getImageViewMatrix());
    }

    // Unchanged from ImageViewTouchBase
    protected float getValue(Matrix matrix, int whichValue)
    {
        matrix.getValues(mMatrixValues);
        return mMatrixValues[whichValue];
    }

    // Get the scale factor out of the matrix.
    protected float getScale(Matrix matrix)
    {

        // If the bitmap is set return the scale
        if (mBitmap != null)
            return getValue(matrix, Matrix.MSCALE_X);
            // Otherwise return the default value of 1
        else
            return 1f;
    }

    // Returns the current scale of the view
    public float getScale()
    {
        return getScale(mSuppMatrix);
    }

    // Setup the base matrix so that the image is centered and scaled properly.
    private void setBaseMatrix(Bitmap bitmap, Matrix matrix)
    {
        float scale;
        float viewWidth = getWidth();
        float viewHeight = getHeight();
        float widthScale = viewWidth / bitmap.getWidth();
        float heightScale = viewHeight / bitmap.getHeight();

        mMinZoom = 1f;

        // Fit screen
        scale = Math.min(heightScale, widthScale);

        if (Mango.getSharedPreferences().getString("defaultScaleMode", "0").equals("0"))
        {
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) // fit width
                scale = widthScale;
        }
        else if (Mango.getSharedPreferences().getString("defaultScaleMode", "0").equals("1"))
            scale = widthScale;
        else if (Mango.getSharedPreferences().getString("defaultScaleMode", "0").equals("2"))
            scale = heightScale;
        else if (Mango.getSharedPreferences().getString("defaultScaleMode", "0").equals("4"))
        {
            scale = 1f;
            mMinZoom = Math.min(1f, widthScale);
        }

        matrix.reset();

        matrix.setScale(scale, scale);
        matrix.postTranslate((viewWidth - (bitmap.getWidth() * scale)) / 2F, (viewHeight - (bitmap.getHeight() * scale)) / 2F);

        if (Mango.getSharedPreferences().getBoolean("leftRightReading", false))
            scrollToTopLeftCorner();
        else
            scrollToTopRightCorner();

        // Mango.Log("Imageviewer", "setBaseMatrix: scale = " + scale + ", translate X =  " + (viewWidth -
        // (bitmap.getWidth() * scale)) / 2F);
    }

    // Combine the base matrix and the supp matrix to make the final matrix.
    protected Matrix getImageViewMatrix()
    {
        mDisplayMatrix.set(mBaseMatrix);
        mDisplayMatrix.postConcat(mSuppMatrix);
        return mDisplayMatrix;
    }

    // Sets the maximum zoom, which is a scale relative to the base matrix. It is calculated to show
    // the image at 400% zoom regardless of screen or image orientation. If in the future we decode
    // the full 3 megapixel image, rather than the current 1024x768, this should be changed down to
    // 200%.
    protected float maxZoom()
    {
        if (mBitmap == null)
            return 1F;

        float fw = (float) mBitmap.getWidth() / (float) mThisWidth;
        float fh = (float) mBitmap.getHeight() / (float) mThisHeight;
        float max = Math.max(fw, fh) * 16;
        return max;
    }

    // Tries to make best use of the space by zooming the picture
    public float zoomDefault()
    {
        if (mBitmap == null)
            return 1F;

        return 1f;
    }

    // Unchanged from ImageViewTouchBase
    public void zoomTo(float scale, float centerX, float centerY)
    {
        if (scale > mMaxZoom)
        {
            scale = mMaxZoom;
        }

        float oldScale = getScale();
        float deltaScale = scale / oldScale;

        mSuppMatrix.postScale(deltaScale, deltaScale, centerX, centerY);
        setImageMatrix(getImageViewMatrix());
        center(true, true, false);
    }

    // Unchanged from ImageViewTouchBase
    protected void zoomTo(final float scale, final float centerX, final float centerY, final float durationMs)
    {
        final float oldScale = getScale();
        final long startTime = System.currentTimeMillis();

        // Setup the zoom runnable
        post(new Runnable()
        {
            @Override
            public void run()
            {
                long now = System.currentTimeMillis();
                float currentMs = Math.min(durationMs, (now - startTime));
                float target;

                if (oldScale > scale)
                {
                    target = oldScale - easeOut(currentMs, 0, scale * (oldScale - 1), durationMs);
                }
                else
                {
                    target = oldScale + easeOut(currentMs, 0, scale, durationMs);
                }

                zoomTo(target, centerX, centerY);

                if (currentMs < durationMs)
                {
                    post(this);
                }
            }
        });
    }

    public void zoomToDefault(boolean animate)
    {
        float width = getWidth();
        float height = getHeight();

        if (animate)
            zoomTo(zoomDefault(), width / 2F, height / 2F, ZOOM_ANIMATION_DURATION);
        else
            zoomTo(zoomDefault(), width / 2F, height / 2F);
    }

    // Unchanged from ImageViewTouchBase
    protected void zoomIn()
    {
        zoomIn(sScaleRate);
    }

    // Unchanged from ImageViewTouchBase
    protected void zoomOut()
    {
        zoomOut(sScaleRate);
    }

    // Unchanged from ImageViewTouchBase
    protected void zoomIn(float rate)
    {
        if (getScale() >= mMaxZoom)
        {
            return; // Don't let the user zoom into the molecular level.
        }
        if (mBitmap == null)
        {
            return;
        }

        float width = getWidth();
        float height = getHeight();

        mSuppMatrix.postScale(rate, rate, width / 2F, height / 2F);
        setImageMatrix(getImageViewMatrix());

    }

    // Unchanged from ImageViewTouchBase
    protected void zoomOut(float rate)
    {
        if (mBitmap == null)
        {
            return;
        }

        float width = getWidth();
        float height = getHeight();

        Matrix tmp = new Matrix(mSuppMatrix);
        tmp.postScale(1F / sScaleRate, 1F / sScaleRate, width / 2F, height / 2F);
        if (getScale(tmp) < 1F)
        {
            mSuppMatrix.setScale(1F, 1F, width / 2F, height / 2F);
        }
        else
        {
            mSuppMatrix.postScale(1F / rate, 1F / rate, width / 2F, height / 2F);
        }
        setImageMatrix(getImageViewMatrix());
        center(true, true, false);

    }

    // Unchanged from ImageViewTouchBase
    protected void postTranslate(float dx, float dy)
    {
        mSuppMatrix.postTranslate(dx, dy);
    }

    // Fling a view by a distance over time
    protected void scrollBy(float distanceX, float distanceY)
    {
        final float dx = distanceX;
        final float dy = distanceY;

        final long startTime = System.currentTimeMillis();

        final float durationMs = PAN_ANIMATION_DURATION;

        mFling = new Runnable()
        {
            float old_x = 0;
            float old_y = 0;

            @Override
            public void run()
            {
                long now = System.currentTimeMillis();
                float currentMs = Math.min(durationMs, now - startTime);
                float x = easeOut(currentMs, 0, dx, durationMs);
                float y = easeOut(currentMs, 0, dy, durationMs);
                postTranslate((x - old_x), (y - old_y));
                center(true, true, false);

                old_x = x;
                old_y = y;
                if (currentMs < durationMs)
                {
                    post(this);
                }
            }
        };
        post(mFling);
    }

    // Gradually slows down a fling velocity
    private float easeOut(float time, float start, float end, float duration)
    {
        return end * ((time = time / duration - 1) * time * time + 1) + start;
    }

    // Custom draw operation to draw the bitmap using mMatrix
    @Override
    public void onDraw(Canvas canvas)
    {
        // Check if the bitmap was ever set
        if (mBitmap != null && !mBitmap.isRecycled())
        {

            // If the current version is above Gingerbread and the layer type is
            // hardware accelerated, the paint is no longer needed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && getLayerType() == View.LAYER_TYPE_HARDWARE)
            {
                canvas.drawBitmap(mBitmap, mMatrix, null);
            }
            else
            {
                // Check if the time between draws has been met and draw the bitmap
                if (((System.currentTimeMillis() - mLastDraw) > sPaintDelay) || !mEnableFastRendering)
                {
                    canvas.drawBitmap(mBitmap, mMatrix, mPaint);
                    mLastDraw = System.currentTimeMillis();
                }

                // Otherwise draw the bitmap without the paint and resubmit a new request
                else
                {
                    canvas.drawBitmap(mBitmap, mMatrix, null);
                    removeCallbacks(mRefresh);
                    postDelayed(mRefresh, sPaintDelay);
                }
            }
        }
    }

    // Adjusts the zoom of the view
    class ScaleListener extends ScaleGestureDetectorCompat.SimpleOnScaleGestureListener
    {

        @Override
        public boolean onScale(ScaleGestureDetectorCompat detector)
        {
            mInvalidateTapTimer = System.currentTimeMillis() + 500;

            // Check if the detector is in progress in order to proceed
            if (detector != null && detector.isInProgress())
            {
                try
                {
                    // Grab the scale
                    float targetScale = getScale() * detector.getScaleFactor();
                    // Correct for the min scale
                    targetScale = Math.min(maxZoom(), Math.max(targetScale, mMinZoom));

                    // Zoom and invalidate the view
                    zoomTo(targetScale, detector.getFocusX(), detector.getFocusY());
                    invalidate();

                    return true;
                }
                catch (IllegalArgumentException e)
                {
                    e.printStackTrace();
                }
            }
            return false;
        }
    }

    private void singleTapAction(MotionEvent event)
    {
        if (System.currentTimeMillis() < mInvalidateTapTimer)
            return;

        Display display = ((WindowManager) Mango.CONTEXT.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        float percent = event.getX() / display.getWidth();

        if (Mango.getSharedPreferences().getBoolean("disableTapZones", false))
        {
            mTapCallback.run();
        }
        else if (!Mango.getSharedPreferences().getBoolean("leftRightReading", false))
        {
            if (percent < 0.8)
            {
                if (mTapCallback != null)
                    mTapCallback.run();
            }
            else
            {
                if (mBackTapCallback != null)
                    mBackTapCallback.run();
            }
        }
        else
        {
            if (percent < 0.2)
            {
                if (mBackTapCallback != null)
                    mBackTapCallback.run();
            }
            else
            {
                if (mTapCallback != null)
                    mTapCallback.run();
            }
        }
    }

    // Handles taps and scrolls of the view
    private class TouchGestureListener extends GestureDetector.SimpleOnGestureListener
    {

        @Override
        public boolean onSingleTapUp(MotionEvent event)
        {
            if (Mango.getSharedPreferences().getBoolean("disableDoubleTapZoom", false))
                singleTapAction(event);
            return super.onSingleTapUp(event);
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent event)
        {
            if (!Mango.getSharedPreferences().getBoolean("disableDoubleTapZoom", false))
                singleTapAction(event);
            return false;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY)
        {
            mInvalidateTapTimer = System.currentTimeMillis() + 500;

            // Skip if there are multiple points of contact
            if ((e1 != null && e1.getPointerCount() > 1) || (e2 != null && e2.getPointerCount() > 1) || (mScaleDetector != null && mScaleDetector.isInProgress()))
                return false;

            // // Scroll the bitmap
            removeCallbacks(mFling);
            postTranslate(-distanceX, -distanceY);
            center(true, true, false);

            // Default case
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e)
        {
            if (Mango.getSharedPreferences().getBoolean("disableDoubleTapZoom", false))
                return true;

            String zoomFactorStr = Mango.getSharedPreferences().getString("doubletapZoomFactor", "1.5");
            float zoomFactorFloat = 1.5f;
            try
            {
                zoomFactorFloat = Float.parseFloat(zoomFactorStr);
            }
            catch (NumberFormatException ex)
            {
                zoomFactorFloat = 1.5f;
            }

            if (getScale() > zoomDefault())
            {
                zoomToDefault(true);
            }
            else
            {
                zoomTo(zoomDefault() * zoomFactorFloat, e.getX(), e.getY(), ZOOM_ANIMATION_DURATION);
            }

            // Always true as double tap was performed
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
        {
            mFlingScrollXUp = (int) getValue(mSuppMatrix, Matrix.MTRANS_X);
            mInvalidateTapTimer = System.currentTimeMillis() + 500;
            if (!(Math.abs(velocityY) > Math.abs(velocityX)))
            {
                if (mLeftFlingCallback != null && mFlingScrollXDown == mFlingScrollXUp && Math.abs(velocityX) > 1100)
                {
                    if (velocityX > 0)
                        mRightFlingCallback.run();
                    else
                        mLeftFlingCallback.run();
                    return false;
                }
            }

            if ((e1 != null && e1.getPointerCount() > 1) || (e2 != null && e2.getPointerCount() > 1))
                return false;
            if (mScaleDetector.isInProgress())
                return false;

            try
            {
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if ((Math.abs(velocityX) > 500 || Math.abs(velocityY) > 500) && !Mango.getSharedPreferences().getBoolean("reducedMomentum", false))
                {
                    scrollBy(diffX, diffY);
                    invalidate();
                }
            }
            catch (NullPointerException e)
            {

            }

            return super.onFling(e1, e2, velocityX, velocityY);
        }

        @Override
        public boolean onDown(MotionEvent e)
        {
            mFlingScrollXDown = (int) getValue(mSuppMatrix, Matrix.MTRANS_X);
            return super.onDown(e);
        }

        @Override
        public void onLongPress(MotionEvent e)
        {
            if (System.currentTimeMillis() < mInvalidateTapTimer)
                return;
            Vibrator v = (Vibrator) MangoZoomableImageView.this.getContext().getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(50);
            mLongPressActive = true;
            mLongPressCenterX = (int) e.getX();
            mLongPressCenterY = (int) e.getY();
            mLongPressLastY = (int) e.getY();
            mLongPressScale = getScale();
            super.onLongPress(e);
        }
    }

    // Used to determine if a fling should be treated as a change page
    // gesture or just a regular horizontal scroll
    private int mFlingScrollXDown = -1;
    private int mFlingScrollXUp = -1;

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {

        // If the bitmap was set, check the scale and gesture detectors
        if (mBitmap != null)
        {
            if (mLongPressActive)
            {
                if (event.getAction() == MotionEvent.ACTION_MOVE)
                {
                    float y = event.getY();
                    float scaleDelta = y - mLongPressLastY;
                    float targetScale = getScale() - (scaleDelta * 0.0075f);
                    // Mango.Log("dy: " + y + ", scaledelta: " + scaleDelta + ", target: " + targetScale);
                    targetScale = Math.min(maxZoom(), Math.max(targetScale, mMinZoom));
                    if (y != mLongPressLastY)
                        zoomTo(targetScale, mLongPressCenterX, mLongPressCenterY);
                    mLongPressLastY = y;
                }
                else if (event.getAction() == MotionEvent.ACTION_UP)
                {
                    mLongPressActive = false;
                }
            }
            else
            {
                // Check the scale detector
                mScaleDetector.onTouchEvent(event);

                // Check the gesture detector
                if (!mScaleDetector.isInProgress())
                    mGestureDetector.onTouchEvent(event);
            }
        }

        // Default case
        return true;
    }

    public void scrollDown()
    {
        scrollBy(getValue(mSuppMatrix, Matrix.MSCALE_X), getValue(mSuppMatrix, Matrix.MSCALE_Y) - 110);
    }

    public void scrollUp()
    {
        scrollBy(getValue(mSuppMatrix, Matrix.MSCALE_X), getValue(mSuppMatrix, Matrix.MSCALE_Y) + 110);
    }

    public void scrollLeft()
    {
        scrollBy(getValue(mSuppMatrix, Matrix.MSCALE_X) + 110, getValue(mSuppMatrix, Matrix.MSCALE_Y));
    }

    public void scrollRight()
    {
        scrollBy(getValue(mSuppMatrix, Matrix.MSCALE_X) - 110, getValue(mSuppMatrix, Matrix.MSCALE_Y));
    }

    public void scrollToTopRightCorner()
    {
        postTranslate(-99999, 99999);
        center(true, true, false);
    }

    public void scrollToTopLeftCorner()
    {
        postTranslate(99999, 99999);
        center(true, true, false);
    }

    public void scrollToTop()
    {
        postTranslate(0, 99999);
        center(true, true, false);
    }

    public void setOnTapCallback(Runnable run)
    {
        mTapCallback = run;
    }

    public void setOnBackTapCallback(Runnable run)
    {
        mBackTapCallback = run;
    }

    public void setOnLeftFlingCallback(Runnable run)
    {
        mLeftFlingCallback = run;
    }

    public void setOnRightFlingCallback(Runnable run)
    {
        mRightFlingCallback = run;
    }

    public void setOnUpdateCallback(Runnable run)
    {
    }

    public void unregisterCallbacks()
    {
        mTapCallback = null;
        mBackTapCallback = null;
        mRightFlingCallback = null;
        mLeftFlingCallback = null;
    }
}