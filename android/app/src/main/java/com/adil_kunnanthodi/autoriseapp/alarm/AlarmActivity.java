package com.adil_kunnanthodi.autoriseapp.alarm;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Full-screen alarm activity that shows over lock screen
 */
public class AlarmActivity extends Activity {
    private static final String TAG = "AlarmActivity";
    
    private String alarmId;
    private String alarmLabel;
    private String alarmTime;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "AlarmActivity created");
        
        setupWindow();
        
        Intent intent = getIntent();
        alarmId = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID);
        alarmLabel = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_LABEL);
        alarmTime = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_TIME);
        
        setContentView(createAlarmView());
    }
    
    private void setupWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager != null) {
                keyguardManager.requestDismissKeyguard(this, null);
            }
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
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
    }
    
    private View createAlarmView() {
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setGravity(android.view.Gravity.CENTER);
        mainLayout.setBackgroundColor(0xFF1976D2);
        mainLayout.setPadding(64, 64, 64, 64);
        
        TextView titleText = new TextView(this);
        titleText.setText("⏰ AutoRise");
        titleText.setTextSize(32);
        titleText.setTextColor(0xFFFFFFFF);
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        titleText.setGravity(android.view.Gravity.CENTER);
        titleText.setPadding(0, 0, 0, 32);
        
        TextView timeText = new TextView(this);
        timeText.setText(alarmTime != null ? alarmTime : "ALARM");
        timeText.setTextSize(64);
        timeText.setTypeface(null, android.graphics.Typeface.BOLD);
        timeText.setTextColor(0xFFFFFFFF);
        timeText.setGravity(android.view.Gravity.CENTER);
        timeText.setPadding(0, 0, 0, 16);
        
        TextView labelText = new TextView(this);
        labelText.setText(alarmLabel != null ? alarmLabel : "Alarm");
        labelText.setTextSize(20);
        labelText.setTextColor(0xFFE3F2FD);
        labelText.setGravity(android.view.Gravity.CENTER);
        labelText.setPadding(0, 0, 0, 64);
        
        Button dismissButton = new Button(this);
        dismissButton.setText("DISMISS ALARM");
        dismissButton.setTextSize(18);
        dismissButton.setTextColor(0xFF1976D2);
        dismissButton.setBackgroundColor(0xFFFFFFFF);
        dismissButton.setPadding(48, 24, 48, 24);
        
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        buttonParams.setMargins(0, 16, 0, 16);
        dismissButton.setLayoutParams(buttonParams);
        dismissButton.setOnClickListener(v -> dismissAlarm());
        
        Button snoozeButton = new Button(this);
        snoozeButton.setText("SNOOZE (5 min)");
        snoozeButton.setTextSize(16);
        snoozeButton.setTextColor(0xFFFFFFFF);
        snoozeButton.setBackgroundColor(0x80FFFFFF);
        snoozeButton.setPadding(32, 16, 32, 16);
        snoozeButton.setLayoutParams(buttonParams);
        snoozeButton.setOnClickListener(v -> snoozeAlarm());
        
        mainLayout.addView(titleText);
        mainLayout.addView(timeText);
        mainLayout.addView(labelText);
        mainLayout.addView(dismissButton);
        mainLayout.addView(snoozeButton);
        
        return mainLayout;
    }
    
    private void dismissAlarm() {
        Log.d(TAG, "Alarm dismissed by user");
        
        Intent serviceIntent = new Intent(this, AlarmService.class);
        serviceIntent.setAction("STOP_ALARM");
        startService(serviceIntent);
        
        Intent dismissIntent = new Intent("com.autoriseapp.ALARM_DISMISSED");
        dismissIntent.putExtra("alarmId", alarmId);
        sendBroadcast(dismissIntent);
        
        finish();
    }
    
    private void snoozeAlarm() {
        Log.d(TAG, "Alarm snoozed by user for 5 minutes");
        
        Intent serviceIntent = new Intent(this, AlarmService.class);
        serviceIntent.setAction("STOP_ALARM");
        startService(serviceIntent);
        
        long snoozeTime = System.currentTimeMillis() + (5 * 60 * 1000);
        String snoozeId = alarmId + "_snooze_" + System.currentTimeMillis();
        
        try {
            AlarmReceiver.scheduleExactAlarm(this, snoozeId, snoozeTime, "Snooze - " + alarmLabel);
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling snooze alarm", e);
        }
        
        Intent snoozeIntent = new Intent("com.autoriseapp.ALARM_SNOOZED");
        snoozeIntent.putExtra("alarmId", alarmId);
        snoozeIntent.putExtra("snoozeMinutes", 5);
        sendBroadcast(snoozeIntent);
        
        finish();
    }
    
    @Override
    public void onBackPressed() {
        Log.d(TAG, "Back button pressed - ignoring to prevent accidental dismissal");
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        
        alarmId = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID);
        alarmLabel = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_LABEL);
        alarmTime = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_TIME);
        
        setContentView(createAlarmView());
    }
}
