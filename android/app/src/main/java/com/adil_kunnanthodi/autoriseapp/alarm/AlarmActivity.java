package com.adil_kunnanthodi.autoriseapp.alarm;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.adil_kunnanthodi.autoriseapp.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlarmActivity extends Activity {
    private static final String TAG = "AlarmActivity";
    private String alarmId;
    private String alarmLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "AlarmActivity created");
        
        // Get alarm data from intent
        Intent intent = getIntent();
        alarmId = intent.getStringExtra("alarm_id");
        alarmLabel = intent.getStringExtra("alarm_label");
        
        setupWindow();
        setupUI();
    }

    private void setupWindow() {
        // Show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager != null) {
                keyguardManager.requestDismissKeyguard(this, null);
            }
        } else {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                          WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                          WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                          WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                          WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON);
        }

        // Make full screen
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN | 
                       View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                       View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);
    }

    private void setupUI() {
        // Create a simple layout programmatically since we don't have layout resources
        setContentView(createAlarmLayout());
    }

    private View createAlarmLayout() {
        // Create a simple vertical layout for the alarm screen
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.setBackgroundColor(0xFF1976D2); // Blue background
        layout.setPadding(50, 100, 50, 100);

        // Alarm icon/title
        TextView titleView = new TextView(this);
        titleView.setText("⏰");
        titleView.setTextSize(72);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setGravity(android.view.Gravity.CENTER);
        layout.addView(titleView);

        // Alarm label
        TextView labelView = new TextView(this);
        labelView.setText(alarmLabel != null ? alarmLabel : "AutoRise Alarm");
        labelView.setTextSize(24);
        labelView.setTextColor(0xFFFFFFFF);
        labelView.setGravity(android.view.Gravity.CENTER);
        labelView.setPadding(0, 30, 0, 20);
        layout.addView(labelView);

        // Current time
        TextView timeView = new TextView(this);
        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        timeView.setText(timeFormat.format(new Date()));
        timeView.setTextSize(48);
        timeView.setTextColor(0xFFFFFFFF);
        timeView.setGravity(android.view.Gravity.CENTER);
        timeView.setPadding(0, 20, 0, 50);
        layout.addView(timeView);

        // Button container
        android.widget.LinearLayout buttonLayout = new android.widget.LinearLayout(this);
        buttonLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(android.view.Gravity.CENTER);

        // Dismiss button
        Button dismissButton = new Button(this);
        dismissButton.setText("Dismiss");
        dismissButton.setTextSize(18);
        dismissButton.setBackgroundColor(0xFF4CAF50);
        dismissButton.setTextColor(0xFFFFFFFF);
        dismissButton.setPadding(40, 20, 40, 20);
        dismissButton.setOnClickListener(v -> dismissAlarm());
        
        android.widget.LinearLayout.LayoutParams dismissParams = 
            new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            );
        dismissParams.setMargins(20, 20, 20, 20);
        buttonLayout.addView(dismissButton, dismissParams);

        // Snooze button
        Button snoozeButton = new Button(this);
        snoozeButton.setText("Snooze 9min");
        snoozeButton.setTextSize(18);
        snoozeButton.setBackgroundColor(0xFFFF9800);
        snoozeButton.setTextColor(0xFFFFFFFF);
        snoozeButton.setPadding(40, 20, 40, 20);
        snoozeButton.setOnClickListener(v -> snoozeAlarm());
        
        android.widget.LinearLayout.LayoutParams snoozeParams = 
            new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            );
        snoozeParams.setMargins(20, 20, 20, 20);
        buttonLayout.addView(snoozeButton, snoozeParams);

        layout.addView(buttonLayout);

        return layout;
    }

    private void dismissAlarm() {
        Log.d(TAG, "Alarm dismissed by user");
        
        // Stop the alarm service
        Intent serviceIntent = new Intent(this, AlarmService.class);
        serviceIntent.setAction("STOP_ALARM");
        startService(serviceIntent);
        
        // Broadcast alarm dismissed event
        Intent broadcastIntent = new Intent("com.adil_kunnanthodi.autoriseapp.ALARM_DISMISSED");
        broadcastIntent.putExtra("alarm_id", alarmId);
        sendBroadcast(broadcastIntent);
        
        finish();
    }

    private void snoozeAlarm() {
        Log.d(TAG, "Alarm snoozed by user");
        
        // Stop current alarm service
        Intent serviceIntent = new Intent(this, AlarmService.class);
        serviceIntent.setAction("STOP_ALARM");
        startService(serviceIntent);
        
        // Schedule snooze alarm for 9 minutes from now
        scheduleSnoozeAlarm();
        
        // Broadcast alarm snoozed event
        Intent broadcastIntent = new Intent("com.adil_kunnanthodi.autoriseapp.ALARM_SNOOZED");
        broadcastIntent.putExtra("alarm_id", alarmId);
        sendBroadcast(broadcastIntent);
        
        finish();
    }

    private void scheduleSnoozeAlarm() {
        try {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;

            // Check permissions for exact alarms on Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.w(TAG, "Cannot schedule snooze alarm - permission not granted");
                    return;
                }
            }

            String snoozeId = alarmId + "_snooze_" + System.currentTimeMillis();
            String snoozeLabel = (alarmLabel != null ? alarmLabel : "Alarm") + " (Snoozed)";
            
            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.putExtra("alarm_id", snoozeId);
            intent.putExtra("alarm_label", snoozeLabel);
            intent.setAction("com.adil_kunnanthodi.autoriseapp.ALARM_TRIGGER");
            
            int requestCode = snoozeId.hashCode();
            
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Schedule for 9 minutes from now
            long snoozeTime = System.currentTimeMillis() + (9 * 60 * 1000);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    snoozeTime,
                    pendingIntent
                );
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    snoozeTime,
                    pendingIntent
                );
            }
            
            Log.d(TAG, "Scheduled snooze alarm for: " + snoozeId);
            
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling snooze alarm", e);
        }
    }

    @Override
    public void onBackPressed() {
        // Prevent back button from dismissing alarm
        // User must use dismiss or snooze buttons
        Log.d(TAG, "Back button pressed - ignoring (use dismiss or snooze)");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        
        // Handle new alarm while activity is already showing
        String newAlarmId = intent.getStringExtra("alarm_id");
        String newAlarmLabel = intent.getStringExtra("alarm_label");
        
        if (newAlarmId != null) {
            this.alarmId = newAlarmId;
            this.alarmLabel = newAlarmLabel;
            setupUI(); // Refresh UI with new alarm info
        }
    }
}