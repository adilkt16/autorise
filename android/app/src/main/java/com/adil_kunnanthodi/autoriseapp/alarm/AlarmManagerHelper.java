package com.adil_kunnanthodi.autoriseapp.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import java.util.Calendar;

/**
 * AlarmManagerHelper - Manages scheduling and canceling of system alarms
 * Uses AlarmManager.setExactAndAllowWhileIdle() for reliable Doze-compatible alarms
 */
public class AlarmManagerHelper {
    private static final String TAG = "AlarmManagerHelper";
    
    private final Context context;
    private final AlarmManager alarmManager;
    
    public AlarmManagerHelper(Context context) {
        this.context = context;
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }
    
    /**
     * Schedule an exact alarm that works even in Doze mode
     * @param alarmId Unique identifier for the alarm
     * @param hour Hour in 24-hour format (0-23)
     * @param minute Minute (0-59)
     * @param alarmTime Display time string
     * @return true if alarm was scheduled successfully
     */
    public boolean scheduleAlarm(String alarmId, int hour, int minute, String alarmTime) {
        try {
            Log.d(TAG, "📅 Scheduling alarm: " + alarmId + " for " + hour + ":" + minute);
            
            // Check if we have permission to schedule exact alarms (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.e(TAG, "❌ Cannot schedule exact alarms - permission denied");
                    return false;
                }
            }
            
            // Calculate the target time
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            
            // If the time has passed today, schedule for tomorrow
            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_MONTH, 1);
            }
            
            // Create intent for AlarmReceiver
            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.setAction(AlarmReceiver.ACTION_ALARM_TRIGGER);
            intent.putExtra("alarmId", alarmId);
            intent.putExtra("alarmTime", alarmTime);
            
            // Create PendingIntent with unique request code based on alarmId
            int requestCode = alarmId.hashCode();
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            // Schedule the exact alarm using setExactAndAllowWhileIdle
            // This works even when the device is in Doze mode
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                pendingIntent
            );
            
            Log.d(TAG, "✅ Alarm scheduled for: " + calendar.getTime());
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error scheduling alarm: " + alarmId, e);
            return false;
        }
    }
    
    /**
     * Cancel a scheduled alarm
     * @param alarmId The alarm ID to cancel
     * @return true if alarm was canceled successfully
     */
    public boolean cancelAlarm(String alarmId) {
        try {
            Log.d(TAG, "🗑️ Canceling alarm: " + alarmId);
            
            // Create the same intent and PendingIntent as when scheduling
            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.setAction(AlarmReceiver.ACTION_ALARM_TRIGGER);
            intent.putExtra("alarmId", alarmId);
            
            int requestCode = alarmId.hashCode();
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            // Cancel the alarm
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
            
            Log.d(TAG, "✅ Alarm canceled: " + alarmId);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error canceling alarm: " + alarmId, e);
            return false;
        }
    }
    
    /**
     * Check if the app can schedule exact alarms (Android 12+)
     * @return true if exact alarms can be scheduled
     */
    public boolean canScheduleExactAlarms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return alarmManager.canScheduleExactAlarms();
        }
        return true; // Always true for Android < 12
    }
    
    /**
     * Get the next alarm time for debugging
     * @return Information about the next scheduled alarm
     */
    public AlarmManager.AlarmClockInfo getNextAlarmClock() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return alarmManager.getNextAlarmClock();
        }
        return null;
    }
}
