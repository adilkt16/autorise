package com.adil_kunnanthodi.autoriseapp.alarm;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.util.Log;

public class AlarmActivity extends Activity {
    private static final String TAG = "AlarmActivity";
    private String alarmId;
    private String alarmLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "Alarm activity created");
        
        // Show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }

        // Dismiss keyguard
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            keyguardManager.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }

        // Make it full screen and immersive
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }

        // Get alarm details
        Intent intent = getIntent();
        alarmId = intent.getStringExtra("alarmId");
        alarmLabel = intent.getStringExtra("label");
        
        createAlarmUI();
    }

    private void createAlarmUI() {
        // Create simple layout programmatically
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.setBackgroundColor(0xFF1976D2); // Blue background
        layout.setPadding(50, 100, 50, 100);

        // Alarm time display
        TextView timeText = new TextView(this);
        timeText.setText(new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        .format(new java.util.Date()));
        timeText.setTextSize(72);
        timeText.setTextColor(0xFFFFFFFF);
        timeText.setGravity(android.view.Gravity.CENTER);
        timeText.setTypeface(null, android.graphics.Typeface.BOLD);

        // Alarm label
        TextView labelText = new TextView(this);
        labelText.setText(alarmLabel != null ? alarmLabel : "Alarm");
        labelText.setTextSize(24);
        labelText.setTextColor(0xFFFFFFFF);
        labelText.setGravity(android.view.Gravity.CENTER);
        labelText.setPadding(0, 20, 0, 60);

        // Dismiss button
        Button dismissButton = new Button(this);
        dismissButton.setText("DISMISS");
        dismissButton.setTextSize(18);
        dismissButton.setBackgroundColor(0xFFf44336); // Red
        dismissButton.setTextColor(0xFFFFFFFF);
        dismissButton.setPadding(40, 20, 40, 20);
        dismissButton.setOnClickListener(v -> dismissAlarm());

        // Snooze button
        Button snoozeButton = new Button(this);
        snoozeButton.setText("SNOOZE (5 MIN)");
        snoozeButton.setTextSize(18);
        snoozeButton.setBackgroundColor(0xFF4CAF50); // Green
        snoozeButton.setTextColor(0xFFFFFFFF);
        snoozeButton.setPadding(40, 20, 40, 20);
        snoozeButton.setOnClickListener(v -> snoozeAlarm());

        // Button container
        android.widget.LinearLayout buttonLayout = new android.widget.LinearLayout(this);
        buttonLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(android.view.Gravity.CENTER);
        
        android.widget.LinearLayout.LayoutParams buttonParams = 
            new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            );
        buttonParams.setMargins(20, 20, 20, 20);
        
        buttonLayout.addView(dismissButton, buttonParams);
        buttonLayout.addView(snoozeButton, buttonParams);

        // Add views to layout
        layout.addView(timeText);
        layout.addView(labelText);
        layout.addView(buttonLayout);

        setContentView(layout);
    }

    private void dismissAlarm() {
        Log.d(TAG, "Alarm dismissed: " + alarmId);
        
        // Stop the alarm service
        Intent serviceIntent = new Intent(this, AlarmService.class);
        stopService(serviceIntent);
        
        // Broadcast alarm dismissed event
        Intent broadcastIntent = new Intent("com.autorise.ALARM_DISMISSED");
        broadcastIntent.putExtra("alarmId", alarmId);
        sendBroadcast(broadcastIntent);
        
        finish();
    }

    private void snoozeAlarm() {
        Log.d(TAG, "Alarm snoozed: " + alarmId);
        
        // Stop current alarm service
        Intent serviceIntent = new Intent(this, AlarmService.class);
        stopService(serviceIntent);
        
        // Schedule snooze alarm (5 minutes)
        scheduleSnoozeAlarm();
        
        // Broadcast alarm snoozed event
        Intent broadcastIntent = new Intent("com.autorise.ALARM_SNOOZED");
        broadcastIntent.putExtra("alarmId", alarmId);
        sendBroadcast(broadcastIntent);
        
        finish();
    }

    private void scheduleSnoozeAlarm() {
        try {
            long snoozeTime = System.currentTimeMillis() + (5 * 60 * 1000); // 5 minutes
            String snoozeId = alarmId + "_snooze_" + System.currentTimeMillis();
            
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            
            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.putExtra("alarmId", snoozeId);
            intent.putExtra("label", alarmLabel + " (Snoozed)");
            intent.setAction("ALARM_TRIGGER_" + snoozeId);
            
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                snoozeId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent);
            }
            
            Log.d(TAG, "Snooze alarm scheduled for: " + new java.util.Date(snoozeTime));
            
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling snooze alarm", e);
        }
    }

    @Override
    public void onBackPressed() {
        // Prevent back button from dismissing alarm
        // User must use dismiss or snooze buttons
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Alarm activity destroyed");
    }
}
