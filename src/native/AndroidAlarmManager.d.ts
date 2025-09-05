/**
 * AndroidAlarmManager - TypeScript declarations for native Android alarm module
 */

export interface AlarmResult {
  alarmId: string;
  status: 'scheduled' | 'canceled';
  message: string;
}

export interface AlarmPermissionResult {
  canSchedule: boolean;
  androidVersion: number;
  message: string;
}

export interface AndroidAlarmManager {
  /**
   * Schedule an alarm using Android AlarmManager
   * @param alarmId Unique identifier for the alarm
   * @param hour Hour in 24-hour format (0-23)
   * @param minute Minute (0-59)
   * @param alarmTime Display string for the alarm time
   * @returns Promise resolving to alarm result
   */
  scheduleAlarm(
    alarmId: string,
    hour: number,
    minute: number,
    alarmTime: string
  ): Promise<AlarmResult>;

  /**
   * Cancel a scheduled alarm
   * @param alarmId The alarm ID to cancel
   * @returns Promise resolving to alarm result
   */
  cancelAlarm(alarmId: string): Promise<AlarmResult>;

  /**
   * Check if the app can schedule exact alarms
   * @returns Promise resolving to permission status
   */
  canScheduleExactAlarms(): Promise<AlarmPermissionResult>;

  /**
   * Request permission to schedule exact alarms (Android 12+)
   * @returns Promise resolving to permission request status
   */
  requestExactAlarmPermission(): Promise<string>;

  /**
   * Stop any currently ringing alarm
   * @returns Promise resolving to stop request status
   */
  stopAlarm(): Promise<string>;

  /**
   * Request battery optimization whitelist for reliable alarms
   * @returns Promise resolving to request status
   */
  requestBatteryOptimizationDisable(): Promise<string>;
}

declare const AndroidAlarmManager: AndroidAlarmManager;

export default AndroidAlarmManager;
