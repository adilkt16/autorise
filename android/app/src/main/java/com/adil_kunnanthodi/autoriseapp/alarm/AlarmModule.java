package com.adil_kunnanthodi.autoriseapp.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;

import java.util.Calendar;
import java.util.Set;

public class AlarmModule extends ReactContextBaseJavaModule {
    private static final String TAG = "AlarmModule";
    private static final String PREFS_NAME = "AlarmPrefs";
    private ReactApplicationContext reactContext;

    public AlarmModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "AlarmModule";
    }

    @ReactMethod
    public void setAlarm(ReadableMap alarmData, Promise promise) {
        try {
            Context context = getReactApplicationContext();
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            
            int alarmId = alarmData.getInt("id");
            String title = alarmData.getString("title");
            String sound = alarmData.hasKey("sound") ? alarmData.getString("sound") : "alarm_default";
            boolean isEnabled = alarmData.hasKey("isEnabled") ? alarmData.getBoolean("isEnabled") : true;
            
            // Parse time
            String time = alarmData.getString("time");
            String[] timeParts = time.split(":");
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);
            
            // Parse days array
            WritableArray daysArray = new WritableNativeArray();
            if (alarmData.hasKey("days") && alarmData.getArray("days") != null) {
                for (int i = 0; i < alarmData.getArray("days").size(); i++) {
                    daysArray.pushBoolean(alarmData.getArray("days").getBoolean(i));
                }
            }
            
            if (!isEnabled) {
                cancelAlarm(alarmId);
                promise.resolve("Alarm disabled");
                return;
            }
            
            // Set alarm for each selected day
            for (int dayIndex = 0; dayIndex < 7; dayIndex++) {
                if (daysArray.size() > dayIndex && daysArray.getBoolean(dayIndex)) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.set(Calendar.HOUR_OF_DAY, hour);
                    calendar.set(Calendar.MINUTE, minute);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);
                    
                    // Set day of week (Calendar.SUNDAY = 1, so we add 1)
                    calendar.set(Calendar.DAY_OF_WEEK, dayIndex + 1);
                    
                    // If time has passed today, set for next week
                    if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                        calendar.add(Calendar.WEEK_OF_YEAR, 1);
                    }
                    
                    Intent intent = new Intent(context, AlarmReceiver.class);
                    intent.putExtra("alarmId", alarmId);
                    intent.putExtra("title", title);
                    intent.putExtra("sound", sound);
                    intent.putExtra("dayIndex", dayIndex);
                    
                    int requestCode = alarmId * 10 + dayIndex;
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        context, 
                        requestCode, 
                        intent, 
                        PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0)
                    );
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
                    } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
                    }
                    
                    Log.d(TAG, "Alarm set for day " + dayIndex + " at " + calendar.getTime());
                }
            }
            
            // Save alarm data to preferences
            saveAlarmToPrefs(alarmId, title, time, sound, isEnabled, daysArray);
            
            promise.resolve("Alarm set successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting alarm", e);
            promise.reject("ERROR", "Failed to set alarm: " + e.getMessage());
        }
    }
    
    @ReactMethod
    public void cancelAlarm(int alarmId, Promise promise) {
        try {
            cancelAlarm(alarmId);
            promise.resolve("Alarm cancelled");
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to cancel alarm: " + e.getMessage());
        }
    }
    
    private void cancelAlarm(int alarmId) {
        Context context = getReactApplicationContext();
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        
        // Cancel all pending intents for this alarm (for all days)
        for (int dayIndex = 0; dayIndex < 7; dayIndex++) {
            Intent intent = new Intent(context, AlarmReceiver.class);
            int requestCode = alarmId * 10 + dayIndex;
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 
                requestCode, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0)
            );
            alarmManager.cancel(pendingIntent);
        }
        
        // Remove from preferences
        removeAlarmFromPrefs(alarmId);
        
        Log.d(TAG, "Alarm " + alarmId + " cancelled");
    }
    
    @ReactMethod
    public void getAllAlarms(Promise promise) {
        try {
            SharedPreferences prefs = reactContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            Set<String> alarmKeys = prefs.getAll().keySet();
            
            WritableArray alarms = new WritableNativeArray();
            
            for (String key : alarmKeys) {
                if (key.startsWith("alarm_")) {
                    String alarmData = prefs.getString(key, "");
                    if (!alarmData.isEmpty()) {
                        // Parse saved alarm data and add to array
                        WritableMap alarm = parseAlarmData(alarmData);
                        if (alarm != null) {
                            alarms.pushMap(alarm);
                        }
                    }
                }
            }
            
            promise.resolve(alarms);
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting alarms", e);
            promise.reject("ERROR", "Failed to get alarms: " + e.getMessage());
        }
    }
    
    private void saveAlarmToPrefs(int alarmId, String title, String time, String sound, boolean isEnabled, WritableArray days) {
        SharedPreferences prefs = reactContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        // Convert days array to string
        StringBuilder daysStr = new StringBuilder();
        for (int i = 0; i < days.size(); i++) {
            daysStr.append(days.getBoolean(i) ? "1" : "0");
            if (i < days.size() - 1) daysStr.append(",");
        }
        
        String alarmData = title + "|" + time + "|" + sound + "|" + isEnabled + "|" + daysStr.toString();
        editor.putString("alarm_" + alarmId, alarmData);
        editor.apply();
    }
    
    private void removeAlarmFromPrefs(int alarmId) {
        SharedPreferences prefs = reactContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove("alarm_" + alarmId);
        editor.apply();
    }
    
    private WritableMap parseAlarmData(String data) {
        try {
            String[] parts = data.split("\\|");
            if (parts.length >= 5) {
                WritableMap alarm = new WritableNativeMap();
                alarm.putString("title", parts[0]);
                alarm.putString("time", parts[1]);
                alarm.putString("sound", parts[2]);
                alarm.putBoolean("isEnabled", Boolean.parseBoolean(parts[3]));
                
                WritableArray days = new WritableNativeArray();
                String[] dayParts = parts[4].split(",");
                for (String day : dayParts) {
                    days.pushBoolean("1".equals(day));
                }
                alarm.putArray("days", days);
                
                return alarm;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing alarm data", e);
        }
        return null;
    }
}
