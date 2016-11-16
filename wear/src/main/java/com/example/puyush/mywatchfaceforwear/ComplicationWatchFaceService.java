/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.example.puyush.mywatchfaceforwear;
        import android.app.PendingIntent;
        import android.content.BroadcastReceiver;
        import android.content.ComponentName;
        import android.content.Context;
        import android.content.Intent;
        import android.content.IntentFilter;
        import android.graphics.Bitmap;
        import android.graphics.BitmapFactory;
        import android.graphics.Canvas;
        import android.graphics.Color;
        import android.graphics.ColorMatrix;
        import android.graphics.ColorMatrixColorFilter;
        import android.graphics.Paint;
        import android.graphics.Rect;
        import android.graphics.Typeface;
        import android.os.Bundle;
        import android.os.Handler;
        import android.os.Message;
        import android.support.wearable.complications.ComplicationData;
        import android.support.wearable.complications.ComplicationHelperActivity;
        import android.support.wearable.complications.ComplicationText;
        import android.support.wearable.watchface.CanvasWatchFaceService;
        import android.support.wearable.watchface.WatchFaceStyle;
        import android.text.TextUtils;
        import android.util.Log;
        import android.util.SparseArray;
        import android.view.SurfaceHolder;

//        import com.example.android.wearable.complications.R;

        import java.io.Console;
        import java.text.NumberFormat;
        import java.util.Calendar;
        import java.util.TimeZone;
        import java.util.concurrent.TimeUnit;


/**
 * Watch Face for "Adding Complications to your Watch Face" code lab.
 */
public class ComplicationWatchFaceService extends CanvasWatchFaceService {

    private static final String TAG = "ComplicationWatchFace";

    private static final int LEFT_DIAL_COMPLICATION = 0;
    private static final int RIGHT_DIAL_COMPLICATION = 1;

    public static final int[] COMPLICATION_IDS = {LEFT_DIAL_COMPLICATION, RIGHT_DIAL_COMPLICATION};

    // Left and right dial supported types.
    public static final int[][] COMPLICATION_SUPPORTED_TYPES = {
            {ComplicationData.TYPE_SHORT_TEXT},
            {ComplicationData.TYPE_SHORT_TEXT}
    };

    /*
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        private boolean mRegisteredTimeZoneReceiver = false;

        private static final float COMPLICATION_TEXT_SIZE = 38f;
        private static final int COMPLICATION_TAP_BUFFER = 40;

        private static final float HAND_END_CAP_RADIUS = 4f;
        private static final float STROKE_WIDTH = 4f;
        private static final int SHADOW_RADIUS = 6;
        private int FAKE_DATA = 3000;

        private Calendar mCalendar;
        Paint dataPaint = new Paint();

        // Variables for painting Background
        private Paint mBackgroundPaint;
        private Bitmap mBackgroundBitmap;
        private Bitmap mGrayBackgroundBitmap;

        // Variables for painting Complications
        private Paint mComplicationPaint;

        /* To properly place each complication, we need their x and y coordinates. While the width
         * may change from moment to moment based on the time, the height will not change, so we
         * store it as a local variable and only calculate it only when the surface changes
         * (onSurfaceChanged()).
         */
        private int mComplicationsY;

        /* Maps active complication ids to the data for that complication. Note: Data will only be
         * present if the user has chosen a provider via the settings activity for the watch face.
         */
        private SparseArray<ComplicationData> mActiveComplicationDataSparseArray;


        // Watch Face Hand related objects
        private Paint mHandPaint;
        private float mHourHandLength;
        private float mMinuteHandLength;
        private float mSecondHandLength;

        private boolean mAmbient;

        /*
         * Whether the display supports fewer bits for each color in ambient mode.
         * When true, we disable anti-aliasing in ambient mode.
         */
        private boolean mLowBitAmbient;

        /*
         * Whether the display supports burn in protection in ambient mode.
         * When true, remove the background in ambient mode.
         */
        private boolean mBurnInProtection;

        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;
        private float mScale = 1;

        // Handler to update the time once a second in interactive mode.
        private final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if (R.id.message_update == message.what) {
                    invalidate();
                    if (shouldTimerBeRunning()) {
                        long timeMs = System.currentTimeMillis();
                        long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                        mUpdateTimeHandler.sendEmptyMessageDelayed(R.id.message_update, delayMs);
                    }
                }
            }
        };

        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(ComplicationWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setAcceptsTapEvents(true)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mCalendar = Calendar.getInstance();

            initializeBackground(0);

            initializeComplications();

            initializeHands();
        }

        public int tapTouch  = 0;
        private void initializeBackground(int post) {
            int backgroundResId;
            if(post == 0) {
                mBackgroundPaint = new Paint();
                mBackgroundPaint.setColor(Color.BLACK);
                backgroundResId = R.drawable.black;
            }

            else if(post==1) {
                backgroundResId = R.drawable.green;
                FAKE_DATA = FAKE_DATA*2;
                dataPaint.setColor(Color.rgb(252,102,102));
            }
            else if(post == 2) {
                backgroundResId = R.drawable.blue;
                dataPaint.setColor(Color.rgb(255,153,51));
                if( FAKE_DATA > 0) {
                    FAKE_DATA--;
                }
            }
            else if(post == 3) {
                backgroundResId = R.drawable.pink;
                dataPaint.setColor(Color.WHITE);
                FAKE_DATA++;
            }
            else {
                backgroundResId = R.drawable.yellow;
                dataPaint.setColor(Color.rgb(127,0,255));
                FAKE_DATA = FAKE_DATA/2;
            }

            mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), backgroundResId);
        }

        private void initializeComplications() {
            Log.d(TAG, "initializeComplications()");

            mActiveComplicationDataSparseArray = new SparseArray<>(COMPLICATION_IDS.length);

            mComplicationPaint = new Paint();
            mComplicationPaint.setColor(Color.WHITE);
            mComplicationPaint.setTextSize(COMPLICATION_TEXT_SIZE);
            mComplicationPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            mComplicationPaint.setAntiAlias(true);

            setActiveComplications(COMPLICATION_IDS);
        }

        private void initializeHands() {
            mHandPaint = new Paint();
            mHandPaint.setColor(Color.WHITE);
            mHandPaint.setStrokeWidth(STROKE_WIDTH);
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.ROUND);
            mHandPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, Color.BLACK);
            mHandPaint.setStyle(Paint.Style.STROKE);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(R.id.message_update);
            super.onDestroy();
        }

        /*
         * Called when there is updated data for a complication id.
         */
        @Override
        public void onComplicationDataUpdate(
                int complicationId, ComplicationData complicationData) {
            Log.d(TAG, "onComplicationDataUpdate() id: " + complicationId);

            // Adds/updates active complication data in the array.
            mActiveComplicationDataSparseArray.put(complicationId, complicationData);
            invalidate();
        }

        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            // TODO: Step 5, OnTapCommand()
            Log.d(TAG, "OnTapCommand()");
            switch (tapType) {
                case TAP_TYPE_TAP:
                    tapTouch++;

                    int tappedComplicationId = getTappedComplicationId(x, y);

                    if (x<=140 && y<= 140) {
                        Log.e("fucked up", x+" "+y);
                        initializeBackground(1);
                    } else if (x<=140 && y>=140)
                    {
                        initializeBackground(2);
                    }
                    else if (x>=140 && y>=140)
                    {
                        initializeBackground(3);
                    }
                    else
                    {
                        initializeBackground(4);
                    }

                    if (tappedComplicationId != -1) {
                        onComplicationTap(tappedComplicationId);
                    }
                    break;
            }
        }


        public void onDrawText(Canvas canvas, Rect bounds) {
            final Typeface typeface = Typeface.DEFAULT;
            Paint paint = new Paint()
            {{
                setTextSize(40);
                setARGB(255, 255, 255, 255);
                setTypeface(typeface);
            }};
            canvas.drawText("CodeProject", 50, 100, paint);
        }
        /*
         * Determines if tap inside a complication area or returns -1.
         */
        private int getTappedComplicationId(int x, int y) {
            ComplicationData complicationData;
            long currentTimeMillis = System.currentTimeMillis();

            for (int i = 0; i < COMPLICATION_IDS.length; i++) {
                complicationData = mActiveComplicationDataSparseArray.get(COMPLICATION_IDS[i]);

                if ((complicationData != null)
                        && (complicationData.isActive(currentTimeMillis))
                        && (complicationData.getType() != ComplicationData.TYPE_NOT_CONFIGURED)
                        && (complicationData.getType() != ComplicationData.TYPE_EMPTY)) {

                    Rect complicationBoundingRect = new Rect(0, 0, 0, 0);

                    switch (COMPLICATION_IDS[i]) {
                        case LEFT_DIAL_COMPLICATION:
                            complicationBoundingRect.set(
                                    0,                                          // left
                                    mComplicationsY - COMPLICATION_TAP_BUFFER,  // top
                                    (mWidth / 2),                               // right
                                    ((int) COMPLICATION_TEXT_SIZE               // bottom
                                            + mComplicationsY
                                            + COMPLICATION_TAP_BUFFER));
                            break;

                        case RIGHT_DIAL_COMPLICATION:
                            complicationBoundingRect.set(
                                    (mWidth / 2),                               // left
                                    mComplicationsY - COMPLICATION_TAP_BUFFER,  // top
                                    mWidth,                                     // right
                                    ((int) COMPLICATION_TEXT_SIZE
                                            + mComplicationsY
                                            + COMPLICATION_TAP_BUFFER));
                            break;
                    }

                    if (complicationBoundingRect.width() > 0) {
                        if (complicationBoundingRect.contains(x, y)) {
                            return COMPLICATION_IDS[i];
                        }
                    } else {
                        Log.e(TAG, "Not a recognized complication id.");
                    }
                }
            }
            return -1;
        }

        // Fires PendingIntent associated with complication (if it has one).
        private void onComplicationTap(int complicationId) {
            // TODO: Step 5, onComplicationTap()
            Log.d(TAG, "onComplicationTap()");

            ComplicationData complicationData =
                    mActiveComplicationDataSparseArray.get(complicationId);

            if (complicationData != null) {

                if (complicationData.getTapAction() != null) {
                    try {
                        complicationData.getTapAction().send();
                    } catch (PendingIntent.CanceledException e) {
                        Log.e(TAG, "onComplicationTap() tap action error: " + e);
                    }

                } else if (complicationData.getType() == ComplicationData.TYPE_NO_PERMISSION) {

                    // Watch face does not have permission to receive complication data, so launch
                    // permission request.
                    ComponentName componentName = new ComponentName(
                            getApplicationContext(),
                            ComplicationWatchFaceService.class);

                    Intent permissionRequestIntent =
                            ComplicationHelperActivity.createPermissionRequestHelperIntent(
                                    getApplicationContext(), componentName);

                    startActivity(permissionRequestIntent);
                }

            } else {
                Log.d(TAG, "No PendingIntent for complication " + complicationId + ".");
            }
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient || mBurnInProtection) {
                    mHandPaint.setAntiAlias(!inAmbientMode);
                    mComplicationPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            /*
             * Whether the timer should be running depends on whether we're visible (as well as
             * whether we're in ambient mode), so we may need to start or stop the timer.
             */
            updateTimer();
            initializeBackground(0);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mWidth = width;
            mHeight = height;
            /*
             * Find the coordinates of the center point on the screen.
             * Ignore the window insets so that, on round watches
             * with a "chin", the watch face is centered on the entire screen,
             * not just the usable portion.
             */
            mCenterX = mWidth / 2f;
            mCenterY = mHeight / 2f;

            mScale = ((float) width) / (float) mBackgroundBitmap.getWidth();

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * mScale),
                    (int) (mBackgroundBitmap.getHeight() * mScale), true);

            /*
             * Since the height of the complications text does not change, we only have to
             * recalculate when the surface changes.
             */
            mComplicationsY = (int) ((mHeight / 2) + (mComplicationPaint.getTextSize() / 2));

            // Calculate the lengths of the watch hands and store them in member variables.
            mHourHandLength = mCenterX * 0.5f;
            mMinuteHandLength = mCenterX * 0.7f;
            mSecondHandLength = mCenterX * 0.9f;

            if (!mBurnInProtection || !mLowBitAmbient) {
                initGrayBackgroundBitmap();
            }
        }

        private void initGrayBackgroundBitmap() {
            mGrayBackgroundBitmap = Bitmap.createBitmap(mBackgroundBitmap.getWidth(),
                    mBackgroundBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(mGrayBackgroundBitmap);
            Paint grayPaint = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
            grayPaint.setColorFilter(filter);
            canvas.drawBitmap(mBackgroundBitmap, 0, 0, grayPaint);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            drawBackground(canvas);

            drawComplications(canvas, now);

            drawHands(canvas);
            dataHandler(canvas);
        }

        private void dataHandler(Canvas canvas)
        {
            float newXOffset = mCenterX-50;
            float newYOffset = mCenterY+120;
            NumberFormat myFormat = NumberFormat.getInstance();
            myFormat.setGroupingUsed(true);
            String text = myFormat.format(FAKE_DATA);
            onDesiredSizeChanged(100, 100);

            dataPaint.setTextSize(50);
            canvas.drawText(text,  newXOffset, newYOffset, dataPaint);

        }

        private void drawComplications(Canvas canvas, long currentTimeMillis) {
            // TODO: Step 4, drawComplications()
            ComplicationData complicationData;

            for (int i = 0; i < COMPLICATION_IDS.length; i++) {

                complicationData = mActiveComplicationDataSparseArray.get(COMPLICATION_IDS[i]);

                if ((complicationData != null)
                        && (complicationData.isActive(currentTimeMillis))) {

                    // Both Short Text and No Permission Types can be rendered with the same code.
                    // No Permission will display "--" with an Intent to launch a permission prompt.
                    // If you want to support more types, just add a "else if" below with your
                    // rendering code inside.
                    if (complicationData.getType() == ComplicationData.TYPE_SHORT_TEXT
                            || complicationData.getType() == ComplicationData.TYPE_NO_PERMISSION) {

                        ComplicationText mainText = complicationData.getShortText();
                        ComplicationText subText = complicationData.getShortTitle();

                        CharSequence complicationMessage =
                                mainText.getText(getApplicationContext(), currentTimeMillis);

                        /* In most cases you would want the subText (Title) under the
                         * mainText (Text), but to keep it simple for the code lab, we are
                         * concatenating them all on one line.
                         */
                        if (subText != null) {
                            complicationMessage = TextUtils.concat(
                                    complicationMessage,
                                    " ",
                                    subText.getText(getApplicationContext(), currentTimeMillis));
                        }

                        double textWidth =
                                mComplicationPaint.measureText(
                                        complicationMessage,
                                        0,
                                        complicationMessage.length());

                        int complicationsX;

                        if (COMPLICATION_IDS[i] == LEFT_DIAL_COMPLICATION) {
                            complicationsX = (int) ((mWidth / 2) - textWidth) / 2;
                        } else {
                            // RIGHT_DIAL_COMPLICATION calculations
                            int offset = (int) ((mWidth / 2) - textWidth) / 2;
                            complicationsX = (mWidth / 2) + offset;
                        }

                        canvas.drawText(
                                complicationMessage,
                                0,
                                complicationMessage.length(),
                                complicationsX,
                                mComplicationsY,
                                mComplicationPaint);
                    }
                }
            }
        }

        private void drawBackground(Canvas canvas) {
            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK);
            } else if (mAmbient) {
                canvas.drawBitmap(mGrayBackgroundBitmap, 0, 0, mBackgroundPaint);
            } else {
                canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);
            }
        }

        private void drawHands(Canvas canvas) {
            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            final float seconds =
                    (mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f);
            final float secondsRotation = seconds * 6f;

            final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;

            final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;

            // save the canvas state before we begin to rotate it
            canvas.save();

            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            drawHand(canvas, mHourHandLength);

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
            drawHand(canvas, mMinuteHandLength);

            /*
             * Make sure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise we only update the watch face once a minute.
             */
            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY);
                canvas.drawLine(mCenterX, mCenterY - HAND_END_CAP_RADIUS, mCenterX,
                        mCenterY - mSecondHandLength, mHandPaint);
            }
            canvas.drawCircle(mCenterX, mCenterY, HAND_END_CAP_RADIUS, mHandPaint);
            // restore the canvas' original orientation.
            canvas.restore();
        }

        private void drawHand(Canvas canvas, float handLength) {
            canvas.drawRoundRect(mCenterX - HAND_END_CAP_RADIUS, mCenterY - handLength,
                    mCenterX + HAND_END_CAP_RADIUS, mCenterY + HAND_END_CAP_RADIUS,
                    HAND_END_CAP_RADIUS, HAND_END_CAP_RADIUS, mHandPaint);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /*
             * Whether the timer should be running depends on whether we're visible
             * (as well as whether we're in ambient mode),
             * so we may need to start or stop the timer.
             */
            updateTimer();
        }


        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            ComplicationWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            ComplicationWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(R.id.message_update);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(R.id.message_update);
            }
        }

        /*
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }
    }
}
