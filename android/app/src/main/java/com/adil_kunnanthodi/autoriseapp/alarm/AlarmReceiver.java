package com.adil_kunnanthodi.autoriseapp.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

/**
 * AlarmReceiver - Receives AlarmManager broadcasts and starts the alarm service
 * This runs even when the app is closed or the device is in Doze mode
 */
public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmReceiver";
    public static final String ACTION_ALARM_TRIGGER = "com.adil_kunnanthodi.autoriseapp.ALARM_TRIGGER";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received broadcast: " + action);
        
        if (ACTION_ALARM_TRIGGER.equals(action)) {
            handleAlarmTrigger(context, intent);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
                   Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            handleBootCompleted(context);
        }
    }
    
    private void handleAlarmTrigger(Context context, Intent intent) {
        Log.d(TAG, "🚨 ALARM TRIGGERED - Starting foreground service");
        
        // Acquire wake lock to ensure CPU stays awake
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, 
            "AutoRise:AlarmWakeLock"
        );
        wakeLock.acquire(30000); // Hold for 30 seconds max
        
        try {
            // Extract alarm data from intent
            String alarmId = intent.getStringExtra("alarmId");
            String alarmTime = intent.getStringExtra("alarmTime");
            
            // Start the foreground alarm service
            Intent serviceIntent = new Intent(context, AlarmService.class);
            serviceIntent.putExtra("alarmId", alarmId);
            serviceIntent.putExtra("alarmTime", alarmTime);
            serviceIntent.setAction(AlarmService.ACTION_START_ALARM);
            
            // Use startForegroundService for Android 8+
            context.startForegroundService(serviceIntent);
            
            Log.d(TAG, "✅ Alarm service started for alarm: " + alarmId);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error starting alarm service", e);
        } finally {
            // Release wake lock after a short delay
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }
    
    private void handleBootCompleted(Context context) {
        Log.d(TAG, "Device boot completed - Rescheduling alarms");
        // TODO: Reschedule all active alarms after device reboot
        // This would typically read from SharedPreferences or a database
        // and reschedule all active alarms using AlarmManagerHelper
    }
}
