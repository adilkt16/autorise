package com.adil_kunnanthodi.autoriseapp.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.annotation.NonNull;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableNativeArray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class AlarmModule extends ReactContextBaseJavaModule {
    private static final String PREFS_NAME = "AutoRiseAlarms";
    private static final String ALARMS_KEY = "scheduled_alarms";
    private final ReactApplicationContext reactContext;

    public AlarmModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "AlarmModule";
    }

    @ReactMethod
    public void scheduleAlarm(String alarmId, double triggerTime, String label, Promise promise) {
        try {
            Context context = getReactApplicationContext();
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            
            if (alarmManager == null) {
                promise.reject("ALARM_MANAGER_ERROR", "AlarmManager not available");
                return;
            }

            // Check permissions for exact alarms on Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    promise.reject("PERMISSION_ERROR", "Exact alarm permission not granted");
                    return;
                }
            }

            // Create intent for alarm receiver
            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.putExtra("alarm_id", alarmId);
            intent.putExtra("alarm_label", label != null ? label : "Alarm");
            intent.setAction("com.adil_kunnanthodi.autoriseapp.ALARM_TRIGGER");
            
            // Create unique request code from alarm ID
            int requestCode = alarmId.hashCode();
            
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Schedule the exact alarm
            long triggerTimeMillis = (long) triggerTime;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Use setExactAndAllowWhileIdle for maximum reliability
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                );
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                );
            }

            // Store alarm info in SharedPreferences
            saveAlarmToPrefs(alarmId, triggerTimeMillis, label);

            WritableMap result = new WritableNativeMap();
            result.putBoolean("success", true);
            result.putString("message", "Alarm scheduled successfully");
            result.putString("alarmId", alarmId);
            result.putDouble("triggerTime", triggerTimeMillis);
            
            promise.resolve(result);
            
        } catch (Exception e) {
            promise.reject("SCHEDULE_ERROR", "Failed to schedule alarm: " + e.getMessage());
        }
    }

    @ReactMethod
    public void cancelAlarm(String alarmId, Promise promise) {
        try {
            Context context = getReactApplicationContext();
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            
            if (alarmManager == null) {
                promise.reject("ALARM_MANAGER_ERROR", "AlarmManager not available");
                return;
            }

            // Create intent matching the scheduled alarm
            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.setAction("com.adil_kunnanthodi.autoriseapp.ALARM_TRIGGER");
            
            int requestCode = alarmId.hashCode();
            
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Cancel the alarm
            alarmManager.cancel(pendingIntent);
            
            // Remove from SharedPreferences
            removeAlarmFromPrefs(alarmId);

            WritableMap result = new WritableNativeMap();
            result.putBoolean("success", true);
            result.putString("message", "Alarm cancelled successfully");
            result.putString("alarmId", alarmId);
            
            promise.resolve(result);
            
        } catch (Exception e) {
            promise.reject("CANCEL_ERROR", "Failed to cancel alarm: " + e.getMessage());
        }
    }

    @ReactMethod
    public void getAllAlarms(Promise promise) {
        try {
            List<WritableMap> alarms = getAlarmsFromPrefs();
            
            WritableArray alarmsArray = new WritableNativeArray();
            for (WritableMap alarm : alarms) {
                alarmsArray.pushMap(alarm);
            }
            
            promise.resolve(alarmsArray);
            
        } catch (Exception e) {
            promise.reject("GET_ALARMS_ERROR", "Failed to get alarms: " + e.getMessage());
        }
    }

    @ReactMethod
    public void canScheduleExactAlarms(Promise promise) {
        try {
            Context context = getReactApplicationContext();
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            
            WritableMap result = new WritableNativeMap();
            
            if (alarmManager == null) {
                result.putBoolean("canSchedule", false);
                result.putString("message", "AlarmManager not available");
                promise.resolve(result);
                return;
            }

            boolean canSchedule = true;
            String message = "Exact alarms are available";
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                canSchedule = alarmManager.canScheduleExactAlarms();
                if (!canSchedule) {
                    message = "Exact alarm permission not granted. Please enable 'Alarms & reminders' in app settings.";
                } else {
                    message = "Exact alarm permission granted";
                }
            }
            
            result.putBoolean("canSchedule", canSchedule);
            result.putString("message", message);
            result.putInt("androidVersion", Build.VERSION.SDK_INT);
            
            promise.resolve(result);
            
        } catch (Exception e) {
            promise.reject("PERMISSION_CHECK_ERROR", "Failed to check permissions: " + e.getMessage());
        }
    }

    @ReactMethod
    public void requestExactAlarmPermission(Promise promise) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Context context = getReactApplicationContext();
                AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                
                if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                    Intent intent = new Intent();
                    intent.setAction("android.settings.REQUEST_SCHEDULE_EXACT_ALARM");
                    intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    
                    WritableMap result = new WritableNativeMap();
                    result.putBoolean("success", true);
                    result.putString("message", "Permission request opened");
                    promise.resolve(result);
                } else {
                    WritableMap result = new WritableNativeMap();
                    result.putBoolean("success", true);
                    result.putString("message", "Permission already granted");
                    promise.resolve(result);
                }
            } else {
                WritableMap result = new WritableNativeMap();
                result.putBoolean("success", true);
                result.putString("message", "No permission required for this Android version");
                promise.resolve(result);
            }
        } catch (Exception e) {
            promise.reject("PERMISSION_REQUEST_ERROR", "Failed to request permission: " + e.getMessage());
        }
    }

    @ReactMethod
    public void testAlarm(Promise promise) {
        try {
            // Schedule a test alarm for 10 seconds from now
            long currentTime = System.currentTimeMillis();
            long testTime = currentTime + 10000; // 10 seconds
            String testId = "test_alarm_" + currentTime;
            
            scheduleAlarm(testId, testTime, "Test Alarm", promise);
            
        } catch (Exception e) {
            promise.reject("TEST_ERROR", "Failed to schedule test alarm: " + e.getMessage());
        }
    }

    @ReactMethod
    public void isReady(Promise promise) {
        try {
            WritableMap result = new WritableNativeMap();
            result.putBoolean("ready", true);
            result.putString("message", "Native alarm module is ready");
            result.putString("platform", "android");
            promise.resolve(result);
        } catch (Exception e) {
            promise.reject("MODULE_ERROR", "Module not ready: " + e.getMessage());
        }
    }

    private void saveAlarmToPrefs(String alarmId, long triggerTime, String label) {
        try {
            SharedPreferences prefs = getReactApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String existingAlarms = prefs.getString(ALARMS_KEY, "[]");
            
            JSONArray alarmsArray = new JSONArray(existingAlarms);
            
            // Remove existing alarm with same ID
            for (int i = 0; i < alarmsArray.length(); i++) {
                JSONObject alarm = alarmsArray.getJSONObject(i);
                if (alarmId.equals(alarm.getString("id"))) {
                    alarmsArray.remove(i);
                    break;
                }
            }
            
            // Add new alarm
            JSONObject newAlarm = new JSONObject();
            newAlarm.put("id", alarmId);
            newAlarm.put("triggerTime", triggerTime);
            newAlarm.put("label", label != null ? label : "Alarm");
            newAlarm.put("enabled", true);
            
            alarmsArray.put(newAlarm);
            
            prefs.edit().putString(ALARMS_KEY, alarmsArray.toString()).apply();
            
        } catch (JSONException e) {
            // Handle JSON error silently
        }
    }

    private void removeAlarmFromPrefs(String alarmId) {
        try {
            SharedPreferences prefs = getReactApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String existingAlarms = prefs.getString(ALARMS_KEY, "[]");
            
            JSONArray alarmsArray = new JSONArray(existingAlarms);
            
            // Remove alarm with matching ID
            for (int i = 0; i < alarmsArray.length(); i++) {
                JSONObject alarm = alarmsArray.getJSONObject(i);
                if (alarmId.equals(alarm.getString("id"))) {
                    alarmsArray.remove(i);
                    break;
                }
            }
            
            prefs.edit().putString(ALARMS_KEY, alarmsArray.toString()).apply();
            
        } catch (JSONException e) {
            // Handle JSON error silently
        }
    }

    private List<WritableMap> getAlarmsFromPrefs() {
        List<WritableMap> alarmsList = new ArrayList<>();
        
        try {
            SharedPreferences prefs = getReactApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String existingAlarms = prefs.getString(ALARMS_KEY, "[]");
            
            JSONArray alarmsArray = new JSONArray(existingAlarms);
            
            for (int i = 0; i < alarmsArray.length(); i++) {
                JSONObject alarm = alarmsArray.getJSONObject(i);
                
                WritableMap alarmMap = new WritableNativeMap();
                alarmMap.putString("id", alarm.getString("id"));
                alarmMap.putDouble("triggerTime", alarm.getLong("triggerTime"));
                alarmMap.putString("label", alarm.optString("label", "Alarm"));
                alarmMap.putBoolean("enabled", alarm.optBoolean("enabled", true));
                
                alarmsList.add(alarmMap);
            }
            
        } catch (JSONException e) {
            // Handle JSON error silently, return empty list
        }
        
        return alarmsList;
    }
}
