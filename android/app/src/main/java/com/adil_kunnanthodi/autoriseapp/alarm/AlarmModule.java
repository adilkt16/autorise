package com.adil_kunnanthodi.autoriseapp.alarm;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * React Native bridge for production-grade alarm functionality
 */
public class AlarmModule extends ReactContextBaseJavaModule {
    private static final String TAG = "AlarmModule";
    private static final String PREFS_NAME = "AutoRiseAlarms";
    private static final String PREFS_ALARMS_KEY = "stored_alarms";
    
    private final ReactApplicationContext reactContext;
    private final AlarmManager alarmManager;
    private final SharedPreferences prefs;
    
    public AlarmModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.alarmManager = (AlarmManager) reactContext.getSystemService(Context.ALARM_SERVICE);
        this.prefs = reactContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    @Override
    public String getName() {
        return "AlarmModule";
    }
    
    @ReactMethod
    public void scheduleAlarm(ReadableMap alarmData, Promise promise) {
        try {
            String alarmId = alarmData.getString("id");
            long triggerTime = (long) alarmData.getDouble("triggerTime");
            String label = alarmData.hasKey("label") ? alarmData.getString("label") : "Alarm";
            boolean enabled = !alarmData.hasKey("enabled") || alarmData.getBoolean("enabled");
            
            if (!enabled) {
                promise.reject("ALARM_DISABLED", "Alarm is disabled");
                return;
            }
            
            if (triggerTime <= System.currentTimeMillis()) {
                promise.reject("INVALID_TIME", "Alarm time must be in the future");
                return;
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    promise.reject("PERMISSION_DENIED", "Exact alarm permission required. Please grant in Settings.");
                    return;
                }
            }
            
            AlarmReceiver.scheduleExactAlarm(reactContext, alarmId, triggerTime, label);
            storeAlarmData(alarmId, alarmData);
            
            WritableMap response = new WritableNativeMap();
            response.putBoolean("success", true);
            response.putString("message", "Alarm scheduled successfully");
            response.putString("alarmId", alarmId);
            response.putDouble("scheduledTime", triggerTime);
            
            promise.resolve(response);
            Log.d(TAG, "Alarm scheduled: " + alarmId + " for " + new java.util.Date(triggerTime));
            
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling alarm", e);
            promise.reject("SCHEDULE_ERROR", "Failed to schedule alarm: " + e.getMessage());
        }
    }
    
    @ReactMethod
    public void cancelAlarm(String alarmId, Promise promise) {
        try {
            AlarmReceiver.cancelAlarm(reactContext, alarmId);
            removeStoredAlarm(alarmId);
            
            WritableMap response = new WritableNativeMap();
            response.putBoolean("success", true);
            response.putString("message", "Alarm cancelled successfully");
            response.putString("alarmId", alarmId);
            
            promise.resolve(response);
            Log.d(TAG, "Alarm cancelled: " + alarmId);
            
        } catch (Exception e) {
            Log.e(TAG, "Error cancelling alarm", e);
            promise.reject("CANCEL_ERROR", "Failed to cancel alarm: " + e.getMessage());
        }
    }
    
    @ReactMethod
    public void getAllAlarms(Promise promise) {
        try {
            String storedAlarms = prefs.getString(PREFS_ALARMS_KEY, "[]");
            JSONArray jsonArray = new JSONArray(storedAlarms);
            
            WritableMap response = new WritableNativeMap();
            response.putBoolean("success", true);
            response.putString("message", "Alarms retrieved successfully");
            response.putString("alarmsJson", jsonArray.toString());
            
            promise.resolve(response);
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting alarms", e);
            promise.reject("GET_ERROR", "Failed to get alarms: " + e.getMessage());
        }
    }
    
    @ReactMethod
    public void canScheduleExactAlarms(Promise promise) {
        try {
            boolean canSchedule = true;
            String message = "Exact alarms are supported";
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                canSchedule = alarmManager.canScheduleExactAlarms();
                if (!canSchedule) {
                    message = "Exact alarm permission required for reliable alarms";
                }
            }
            
            WritableMap response = new WritableNativeMap();
            response.putBoolean("canSchedule", canSchedule);
            response.putString("message", message);
            response.putInt("androidVersion", Build.VERSION.SDK_INT);
            
            promise.resolve(response);
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking exact alarm permission", e);
            promise.reject("PERMISSION_CHECK_ERROR", "Failed to check permissions: " + e.getMessage());
        }
    }
    
    @ReactMethod
    public void requestExactAlarmPermission(Promise promise) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                reactContext.startActivity(intent);
                
                promise.resolve("Permission request sent to system settings");
            } else {
                promise.resolve("Exact alarm permission not required on this Android version");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error requesting exact alarm permission", e);
            promise.reject("PERMISSION_REQUEST_ERROR", "Failed to request permission: " + e.getMessage());
        }
    }
    
    @ReactMethod
    public void testAlarm(Promise promise) {
        try {
            String testId = "test_alarm_" + System.currentTimeMillis();
            long testTime = System.currentTimeMillis() + 10000;
            String testLabel = "Test Alarm - AutoRise";
            
            AlarmReceiver.scheduleExactAlarm(reactContext, testId, testTime, testLabel);
            
            WritableMap response = new WritableNativeMap();
            response.putBoolean("success", true);
            response.putString("message", "Test alarm scheduled for 10 seconds");
            response.putString("testAlarmId", testId);
            response.putDouble("testTime", testTime);
            
            promise.resolve(response);
            Log.d(TAG, "Test alarm scheduled: " + testId);
            
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling test alarm", e);
            promise.reject("TEST_ERROR", "Failed to schedule test alarm: " + e.getMessage());
        }
    }
    
    private void storeAlarmData(String alarmId, ReadableMap alarmData) {
        try {
            String storedAlarms = prefs.getString(PREFS_ALARMS_KEY, "[]");
            JSONArray jsonArray = new JSONArray(storedAlarms);
            
            for (int i = jsonArray.length() - 1; i >= 0; i--) {
                JSONObject alarmJson = jsonArray.getJSONObject(i);
                if (alarmId.equals(alarmJson.getString("id"))) {
                    jsonArray.remove(i);
                    break;
                }
            }
            
            JSONObject newAlarm = new JSONObject();
            newAlarm.put("id", alarmData.getString("id"));
            newAlarm.put("triggerTime", alarmData.getDouble("triggerTime"));
            newAlarm.put("label", alarmData.hasKey("label") ? alarmData.getString("label") : "Alarm");
            newAlarm.put("enabled", !alarmData.hasKey("enabled") || alarmData.getBoolean("enabled"));
            
            jsonArray.put(newAlarm);
            prefs.edit().putString(PREFS_ALARMS_KEY, jsonArray.toString()).apply();
            
        } catch (JSONException e) {
            Log.e(TAG, "Error storing alarm data", e);
        }
    }
    
    private void removeStoredAlarm(String alarmId) {
        try {
            String storedAlarms = prefs.getString(PREFS_ALARMS_KEY, "[]");
            JSONArray jsonArray = new JSONArray(storedAlarms);
            
            for (int i = jsonArray.length() - 1; i >= 0; i--) {
                JSONObject alarmJson = jsonArray.getJSONObject(i);
                if (alarmId.equals(alarmJson.getString("id"))) {
                    jsonArray.remove(i);
                    break;
                }
            }
            
            prefs.edit().putString(PREFS_ALARMS_KEY, jsonArray.toString()).apply();
            
        } catch (JSONException e) {
            Log.e(TAG, "Error removing stored alarm", e);
        }
    }
}
