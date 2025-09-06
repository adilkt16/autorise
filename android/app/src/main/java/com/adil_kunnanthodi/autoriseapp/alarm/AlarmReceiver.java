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

public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmReceiver";
    private static final String PREFS_NAME = "AutoRiseAlarms";
    private static final String ALARMS_KEY = "scheduled_alarms";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "AlarmReceiver triggered: " + intent.getAction());

        String action = intent.getAction();
        
        if ("com.adil_kunnanthodi.autoriseapp.ALARM_TRIGGER".equals(action)) {
            handleAlarmTrigger(context, intent);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
                   Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
                   Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
            restoreAlarms(context);
        }
    }

    private void handleAlarmTrigger(Context context, Intent intent) {
        // Acquire wake lock to ensure device stays awake
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;
        
        try {
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "AutoRise:AlarmWakeLock"
                );
                wakeLock.acquire(10 * 60 * 1000L); // 10 minutes max
            }

            String alarmId = intent.getStringExtra("alarm_id");
            String alarmLabel = intent.getStringExtra("alarm_label");
            
            Log.d(TAG, "Alarm triggered - ID: " + alarmId + ", Label: " + alarmLabel);

            // Start the alarm service for audio playback
            Intent serviceIntent = new Intent(context, AlarmService.class);
            serviceIntent.putExtra("alarm_id", alarmId);
            serviceIntent.putExtra("alarm_label", alarmLabel != null ? alarmLabel : "Alarm");
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            // Start the alarm activity to show over lock screen
            Intent activityIntent = new Intent(context, AlarmActivity.class);
            activityIntent.putExtra("alarm_id", alarmId);
            activityIntent.putExtra("alarm_label", alarmLabel != null ? alarmLabel : "Alarm");
            activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                                  Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                  Intent.FLAG_ACTIVITY_SINGLE_TOP);
            
            context.startActivity(activityIntent);

        } catch (Exception e) {
            Log.e(TAG, "Error handling alarm trigger", e);
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    private void restoreAlarms(Context context) {
        Log.d(TAG, "Restoring alarms after system restart");
        
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String alarmsJson = prefs.getString(ALARMS_KEY, "[]");
            
            JSONArray alarmsArray = new JSONArray(alarmsJson);
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            
            if (alarmManager == null) {
                Log.e(TAG, "AlarmManager not available for restoring alarms");
                return;
            }

            long currentTime = System.currentTimeMillis();
            
            for (int i = 0; i < alarmsArray.length(); i++) {
                try {
                    JSONObject alarm = alarmsArray.getJSONObject(i);
                    String alarmId = alarm.getString("id");
                    long triggerTime = alarm.getLong("triggerTime");
                    String label = alarm.optString("label", "Alarm");
                    
                    // Only restore future alarms
                    if (triggerTime > currentTime) {
                        scheduleExactAlarm(context, alarmManager, alarmId, triggerTime, label);
                        Log.d(TAG, "Restored alarm: " + alarmId);
                    } else {
                        Log.d(TAG, "Skipping past alarm: " + alarmId);
                    }
                    
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing alarm data", e);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error restoring alarms", e);
        }
    }

    private void scheduleExactAlarm(Context context, AlarmManager alarmManager, 
                                  String alarmId, long triggerTime, String label) {
        try {
            // Check permissions for exact alarms on Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.w(TAG, "Cannot schedule exact alarms - permission not granted");
                    return;
                }
            }

            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.putExtra("alarm_id", alarmId);
            intent.putExtra("alarm_label", label);
            intent.setAction("com.adil_kunnanthodi.autoriseapp.ALARM_TRIGGER");
            
            int requestCode = alarmId.hashCode();
            
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Use setExactAndAllowWhileIdle for maximum reliability
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                );
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                );
            }
            
            Log.d(TAG, "Scheduled exact alarm for: " + alarmId);
            
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling exact alarm", e);
        }
    }
}