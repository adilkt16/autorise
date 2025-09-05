package com.adil_kunnanthodi.autoriseapp.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Production-grade AlarmReceiver that handles:
 * - Alarm triggers with wake locks
 * - System restarts (BOOT_COMPLETED)
 * - Package updates (MY_PACKAGE_REPLACED)
 */
public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmReceiver";
    private static final String ACTION_ALARM_TRIGGER = "com.autoriseapp.ALARM_TRIGGER";
    public static final String EXTRA_ALARM_ID = "alarm_id";
    public static final String EXTRA_ALARM_LABEL = "alarm_label";
    public static final String EXTRA_ALARM_TIME = "alarm_time";
    private static final String PREFS_NAME = "AutoRiseAlarms";
    private static final String PREFS_ALARMS_KEY = "stored_alarms";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received broadcast: " + action);

        if (ACTION_ALARM_TRIGGER.equals(action)) {
            handleAlarmTrigger(context, intent);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
                   Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            handleSystemRestart(context);
        }
    }

    /**
     * Handle alarm trigger - start foreground service with wake lock
     */
    private void handleAlarmTrigger(Context context, Intent intent) {
        Log.i(TAG, "Alarm triggered!");
        
        // Acquire wake lock to ensure device stays awake
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, 
            "AutoRise:AlarmTrigger"
        );
        wakeLock.acquire(60000); // 1 minute timeout

        try {
            String alarmId = intent.getStringExtra(EXTRA_ALARM_ID);
            String alarmLabel = intent.getStringExtra(EXTRA_ALARM_LABEL);
            String alarmTime = intent.getStringExtra(EXTRA_ALARM_TIME);

            Log.d(TAG, "Starting alarm service for: " + alarmId + " - " + alarmLabel);

            // Start foreground service for alarm playback
            Intent serviceIntent = new Intent(context, AlarmService.class);
            serviceIntent.putExtra(EXTRA_ALARM_ID, alarmId);
            serviceIntent.putExtra(EXTRA_ALARM_LABEL, alarmLabel);
            serviceIntent.putExtra(EXTRA_ALARM_TIME, alarmTime);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            // Start alarm activity to show over lock screen
            Intent activityIntent = new Intent(context, AlarmActivity.class);
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                                   Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                   Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activityIntent.putExtra(EXTRA_ALARM_ID, alarmId);
            activityIntent.putExtra(EXTRA_ALARM_LABEL, alarmLabel);
            activityIntent.putExtra(EXTRA_ALARM_TIME, alarmTime);
            
            context.startActivity(activityIntent);

        } finally {
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    /**
     * Handle system restart - restore all active alarms
     */
    private void handleSystemRestart(Context context) {
        Log.i(TAG, "System restarted, restoring alarms...");
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String storedAlarms = prefs.getString(PREFS_ALARMS_KEY, "[]");
        
        try {
            JSONArray jsonArray = new JSONArray(storedAlarms);
            long currentTime = System.currentTimeMillis();
            int restoredCount = 0;
            
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject alarmJson = jsonArray.getJSONObject(i);
                
                long triggerTime = alarmJson.getLong("triggerTime");
                boolean enabled = alarmJson.optBoolean("enabled", true);
                
                // Only restore future alarms that are enabled
                if (triggerTime > currentTime && enabled) {
                    String alarmId = alarmJson.getString("id");
                    String label = alarmJson.optString("label", "Alarm");
                    
                    scheduleExactAlarm(context, alarmId, triggerTime, label);
                    restoredCount++;
                    Log.d(TAG, "Restored alarm: " + alarmId + " for " + new java.util.Date(triggerTime));
                }
            }
            
            Log.i(TAG, "Restored " + restoredCount + " alarms after system restart");
            
        } catch (JSONException e) {
            Log.e(TAG, "Error restoring alarms", e);
        }
    }

    /**
     * Schedule an exact alarm using AlarmManager
     */
    public static void scheduleExactAlarm(Context context, String alarmId, long triggerTime, String label) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = createAlarmPendingIntent(context, alarmId, label, triggerTime);
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Use setExactAndAllowWhileIdle for maximum reliability (bypasses Doze mode)
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                Log.d(TAG, "Scheduled exact alarm with setExactAndAllowWhileIdle");
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                Log.d(TAG, "Scheduled exact alarm with setExact");
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                Log.d(TAG, "Scheduled alarm with set");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for exact alarms", e);
            throw e;
        }
    }

    /**
     * Cancel a scheduled alarm
     */
    public static void cancelAlarm(Context context, String alarmId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = createAlarmPendingIntent(context, alarmId, "", 0);
        alarmManager.cancel(pendingIntent);
        Log.d(TAG, "Cancelled alarm: " + alarmId);
    }

    /**
     * Create PendingIntent for alarm with proper flags
     */
    public static PendingIntent createAlarmPendingIntent(Context context, String alarmId, String label, long triggerTime) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(ACTION_ALARM_TRIGGER);
        intent.putExtra(EXTRA_ALARM_ID, alarmId);
        intent.putExtra(EXTRA_ALARM_LABEL, label);
        intent.putExtra(EXTRA_ALARM_TIME, new java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(new java.util.Date(triggerTime)));
        
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        
        // Use alarmId hashCode as request code to ensure uniqueness
        return PendingIntent.getBroadcast(context, alarmId.hashCode(), intent, flags);
    }
}
