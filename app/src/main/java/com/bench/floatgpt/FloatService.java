package com.bench.floatgpt;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.AudioManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class FloatService extends Service {

    private WindowManager wm;
    private View bubble;
    private WindowManager.LayoutParams params;
    private boolean bubbleVisible = true;
    private int lastX = 20, lastY = 300;

    private static final int VOLUME_PRESS_COUNT = 3;
    private static final long VOLUME_WINDOW_MS  = 1500;
    private int volumeCount = 0;
    private long firstVolumeTime = 0;
    private static final String CH = "float_ch";

    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (!AudioManager.ACTION_VOLUME_CHANGED.equals(intent.getAction())) return;
            long now = System.currentTimeMillis();
            if (volumeCount == 0 || now - firstVolumeTime > VOLUME_WINDOW_MS) {
                volumeCount = 1;
                firstVolumeTime = now;
            } else {
                volumeCount++;
            }
            if (volumeCount >= VOLUME_PRESS_COUNT) {
                volumeCount = 0;
                toggleBubble();
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createChannel();
        startForeground(1, buildStealthNotif());
        showBubble();
        registerVolumeReceiver();
        return START_STICKY;
    }

    private void showBubble() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        bubble = makeBubbleView();
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(
                dpToPx(52), dpToPx(52), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = lastX;
        params.y = lastY;
        params.alpha = 0.85f;
        wm.addView(bubble, params);
        attachDragAndTap();
    }

    private View makeBubbleView() {
        TextView tv = new TextView(this);
        tv.setText("G");
        tv.setTextSize(20f);
        tv.setTextColor(Color.WHITE);
        tv.setGravity(Gravity.CENTER);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#10a37f"));
        bg.setStroke(2, Color.parseColor("#0d8a6a"));
        tv.setBackground(bg);
        tv.setElevation(8f);
        return tv;
    }

    private void attachDragAndTap() {
        final long[]    lastDown   = {0};
        final int[]     initX      = {0}, initY = {0};
        final float[]   initTouchX = {0}, initTouchY = {0};
        final boolean[] moved      = {false};

        bubble.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastDown[0]   = System.currentTimeMillis();
                    initX[0]      = params.x;
                    initY[0]      = params.y;
                    initTouchX[0] = e.getRawX();
                    initTouchY[0] = e.getRawY();
                    moved[0]      = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int)(e.getRawX() - initTouchX[0]);
                    int dy = (int)(e.getRawY() - initTouchY[0]);
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                        moved[0] = true;
                        params.x = initX[0] + dx;
                        params.y = initY[0] + dy;
                        lastX    = params.x;
                        lastY    = params.y;
                        wm.updateViewLayout(bubble, params);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    long dur = System.currentTimeMillis() - lastDown[0];
                    if (!moved[0] && dur < 300) openWebView();
                    break;
            }
            return true;
        });
    }

    private void toggleBubble() {
        if (bubble == null) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            if (bubbleVisible) {
                wm.removeView(bubble);
                bubbleVisible = false;
            } else {
                params.x = lastX;
                params.y = lastY;
                wm.addView(bubble, params);
                attachDragAndTap();
                bubbleVisible = true;
            }
        });
    }

    private void openWebView() {
        Intent i = new Intent(this, WebViewActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(i);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CH, "Service", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            ch.setSound(null, null);
            ch.enableLights(false);
            ch.enableVibration(false);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildStealthNotif() {
        return new NotificationCompat.Builder(this, CH)
                .setContentTitle("System UI")
                .setContentText("")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setSilent(true)
                .build();
    }

    private void registerVolumeReceiver() {
        IntentFilter f = new IntentFilter(AudioManager.ACTION_VOLUME_CHANGED);
        registerReceiver(volumeReceiver, f);
    }

    private int dpToPx(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(volumeReceiver); } catch (Exception ignored) {}
        if (bubble != null && wm != null) {
            try { wm.removeView(bubble); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent i) { return null; }
}
