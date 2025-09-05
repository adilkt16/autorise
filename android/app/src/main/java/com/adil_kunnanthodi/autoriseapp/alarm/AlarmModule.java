package com.adil_kunnanthodi.autoriseapp.alarm;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;

import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.os.Build;
import android.util.Log;

/**
 * AlarmModule - React Native bridge to native Android alarm functionality
 */
public class AlarmModule extends ReactContextBaseJavaModule {
    private static final String TAG = "AlarmModule";
    private static final String MODULE_NAME = "AndroidAlarmManager";
    
    private final AlarmManagerHelper alarmHelper;
    
    public AlarmModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.alarmHelper = new AlarmManagerHelper(reactContext);
    }
    
    @Override
    public String getName() {
        return MODULE_NAME;
    }
    
    /**
     * Schedule an alarm using Android AlarmManager
     * @param alarmId Unique identifier for the alarm
     * @param hour Hour in 24-hour format (0-23)
     * @param minute Minute (0-59)
     * @param alarmTime Display string for the alarm time
     * @param promise React Native promise for result
     */
    @ReactMethod
    public void scheduleAlarm(String alarmId, int hour, int minute, String alarmTime, Promise promise) {
        try {
            Log.d(TAG, "📱 React Native requesting alarm schedule: " + alarmId);
            
            // Check permissions first
            if (!alarmHelper.canScheduleExactAlarms()) {
                promise.reject("PERMISSION_DENIED", "Cannot schedule exact alarms. Need user permission.");
                return;
            }
            
            boolean success = alarmHelper.scheduleAlarm(alarmId, hour, minute, alarmTime);
            
            if (success) {
                WritableMap result = Arguments.createMap();
                result.putString("alarmId", alarmId);
                result.putString("status", "scheduled");
                result.putString("message", "Alarm scheduled successfully");
                promise.resolve(result);
                Log.d(TAG, "✅ Alarm scheduled via React Native: " + alarmId);
            } else {
                promise.reject("SCHEDULE_FAILED", "Failed to schedule alarm");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error in scheduleAlarm", e);
            promise.reject("ERROR", e.getMessage());
        }
    }
    
    /**
     * Cancel a scheduled alarm
     * @param alarmId The alarm ID to cancel
     * @param promise React Native promise for result
     */
    @ReactMethod
    public void cancelAlarm(String alarmId, Promise promise) {
        try {
            Log.d(TAG, "📱 React Native requesting alarm cancel: " + alarmId);
            
            boolean success = alarmHelper.cancelAlarm(alarmId);
            
            if (success) {
                WritableMap result = Arguments.createMap();
                result.putString("alarmId", alarmId);
                result.putString("status", "canceled");
                result.putString("message", "Alarm canceled successfully");
                promise.resolve(result);
                Log.d(TAG, "✅ Alarm canceled via React Native: " + alarmId);
            } else {
                promise.reject("CANCEL_FAILED", "Failed to cancel alarm");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error in cancelAlarm", e);
            promise.reject("ERROR", e.getMessage());
        }
    }
    
    /**
     * Check if the app can schedule exact alarms
     * @param promise React Native promise for result
     */
    @ReactMethod
    public void canScheduleExactAlarms(Promise promise) {
        try {
            boolean canSchedule = alarmHelper.canScheduleExactAlarms();
            
            WritableMap result = Arguments.createMap();
            result.putBoolean("canSchedule", canSchedule);
            result.putInt("androidVersion", Build.VERSION.SDK_INT);
            
            if (!canSchedule && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                result.putString("message", "Need SCHEDULE_EXACT_ALARM permission on Android 12+");
            } else {
                result.putString("message", "Can schedule exact alarms");
            }
            
            promise.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error checking alarm permissions", e);
            promise.reject("ERROR", e.getMessage());
        }
    }
    
    /**
     * Request permission to schedule exact alarms (Android 12+)
     * @param promise React Native promise for result
     */
    @ReactMethod
    public void requestExactAlarmPermission(Promise promise) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmHelper.canScheduleExactAlarms()) {
                    // Open the exact alarm permission settings
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    intent.setData(Uri.parse("package:" + getReactApplicationContext().getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    
                    getReactApplicationContext().startActivity(intent);
                    
                    promise.resolve("Permission request opened");
                } else {
                    promise.resolve("Permission already granted");
                }
            } else {
                promise.resolve("Permission not needed on this Android version");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error requesting exact alarm permission", e);
            promise.reject("ERROR", e.getMessage());
        }
    }
    
    /**
     * Stop any currently ringing alarm
     * @param promise React Native promise for result
     */
    @ReactMethod
    public void stopAlarm(Promise promise) {
        try {
            Log.d(TAG, "📱 React Native requesting alarm stop");
            
            // Send intent to stop alarm service
            Intent serviceIntent = new Intent(getReactApplicationContext(), AlarmService.class);
            serviceIntent.setAction(AlarmService.ACTION_STOP_ALARM);
            getReactApplicationContext().startService(serviceIntent);
            
            promise.resolve("Alarm stop requested");
            Log.d(TAG, "✅ Alarm stop requested via React Native");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error stopping alarm", e);
            promise.reject("ERROR", e.getMessage());
        }
    }
    
    /**
     * Request battery optimization whitelist for reliable alarms
     * @param promise React Native promise for result
     */
    @ReactMethod
    public void requestBatteryOptimizationDisable(Promise promise) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getReactApplicationContext().getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                
                getReactApplicationContext().startActivity(intent);
                promise.resolve("Battery optimization settings opened");
            } else {
                promise.resolve("Battery optimization not applicable on this Android version");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error requesting battery optimization disable", e);
            promise.reject("ERROR", e.getMessage());
        }
    }
}
