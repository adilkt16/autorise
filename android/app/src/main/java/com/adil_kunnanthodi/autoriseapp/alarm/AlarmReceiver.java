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
import org.json.JSONObject;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmReceiver";
    private static final String ALARM_PREFS = "AlarmPreferences";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        if (action != null && action.startsWith("ALARM_TRIGGER_")) {
            handleAlarmTrigger(context, intent);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
                   Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            restoreAlarms(context);
        }
    }

    private void handleAlarmTrigger(Context context, Intent intent) {
        PowerManager.WakeLock wakeLock = null;
        
        try {
            // Acquire wake lock to ensure we can process the alarm
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "AutoRise:AlarmWakeLock"
            );
            wakeLock.acquire(30000); // 30 seconds timeout

            String alarmId = intent.getStringExtra("alarmId");
            String label = intent.getStringExtra("label");
            
            Log.d(TAG, "Alarm triggered: " + alarmId + " - " + label);

            // Start the alarm service to play audio
            Intent serviceIntent = new Intent(context, AlarmService.class);
            serviceIntent.putExtra("alarmId", alarmId);
            serviceIntent.putExtra("label", label);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            // Show alarm activity over lock screen
            Intent activityIntent = new Intent(context, AlarmActivity.class);
            activityIntent.putExtra("alarmId", alarmId);
            activityIntent.putExtra("label", label);
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
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
        try {
            SharedPreferences prefs = context.getSharedPreferences(ALARM_PREFS, Context.MODE_PRIVATE);
            String alarmsJson = prefs.getString("alarms", "[]");
            
            JSONArray alarms = new JSONArray(alarmsJson);
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            
            for (int i = 0; i < alarms.length(); i++) {
                JSONObject alarm = alarms.getJSONObject(i);
                String alarmId = alarm.getString("id");
                long triggerTime = alarm.getLong("triggerTime");
                String label = alarm.getString("label");
                
                // Only restore future alarms
                if (triggerTime > System.currentTimeMillis()) {
                    scheduleExactAlarm(context, alarmManager, alarmId, triggerTime, label);
                    Log.d(TAG, "Restored alarm: " + alarmId);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restoring alarms after boot", e);
        }
    }

    private void scheduleExactAlarm(Context context, AlarmManager alarmManager, 
                                  String alarmId, long triggerTime, String label) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("alarmId", alarmId);
        intent.putExtra("label", label);
        intent.setAction("ALARM_TRIGGER_" + alarmId);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context, 
            alarmId.hashCode(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        }
    }
}
