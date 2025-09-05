package com.adil_kunnanthodi.autoriseapp.alarm;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

/**
 * AlarmActivity - Full screen alarm UI that shows over lock screen
 */
public class AlarmActivity extends Activity {
    private static final String TAG = "AlarmActivity";
    
    private String alarmId;
    private String alarmTime;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "AlarmActivity created");
        
        // Get alarm data from intent
        alarmId = getIntent().getStringExtra("alarmId");
        alarmTime = getIntent().getStringExtra("alarmTime");
        
        // Setup full screen over lock screen
        setupLockScreenDisplay();
        
        // Create simple alarm UI
        createAlarmUI();
    }
    
    private void setupLockScreenDisplay() {
        // Turn screen on and show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                           WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                           WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        
        // Dismiss keyguard
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
    }
    
    private void createAlarmUI() {
        // Create simple layout programmatically
        setContentView(createMainLayout());
    }
    
    private View createMainLayout() {
        // Create vertical linear layout
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.setBackgroundColor(0xFFFF0000); // Red background
        layout.setPadding(50, 50, 50, 50);
        
        // Alarm title
        TextView titleView = new TextView(this);
        titleView.setText("🚨 ALARM 🚨");
        titleView.setTextSize(48);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setGravity(android.view.Gravity.CENTER);
        layout.addView(titleView);
        
        // Alarm time
        TextView timeView = new TextView(this);
        timeView.setText(alarmTime != null ? alarmTime : "WAKE UP!");
        timeView.setTextSize(36);
        timeView.setTextColor(0xFFFFFF00); // Yellow
        timeView.setGravity(android.view.Gravity.CENTER);
        android.widget.LinearLayout.LayoutParams timeParams = 
            new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            );
        timeParams.setMargins(0, 50, 0, 100);
        timeView.setLayoutParams(timeParams);
        layout.addView(timeView);
        
        // Dismiss button
        Button dismissButton = new Button(this);
        dismissButton.setText("DISMISS ALARM");
        dismissButton.setTextSize(24);
        dismissButton.setBackgroundColor(0xFF4CAF50); // Green
        dismissButton.setTextColor(0xFFFFFFFF);
        dismissButton.setPadding(50, 30, 50, 30);
        dismissButton.setOnClickListener(v -> dismissAlarm());
        layout.addView(dismissButton);
        
        return layout;
    }
    
    private void dismissAlarm() {
        Log.d(TAG, "🔇 Dismissing alarm");
        
        // Stop the alarm service
        Intent serviceIntent = new Intent(this, AlarmService.class);
        serviceIntent.setAction(AlarmService.ACTION_STOP_ALARM);
        startService(serviceIntent);
        
        // Finish the activity
        finish();
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Handle new alarm if activity is already running
        setIntent(intent);
        alarmId = intent.getStringExtra("alarmId");
        alarmTime = intent.getStringExtra("alarmTime");
    }
    
    @Override
    public void onBackPressed() {
        // Prevent back button from dismissing alarm
        // User must use dismiss button
    }
}
