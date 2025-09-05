import { NativeModules, Platform } from 'react-native';

interface AlarmData {
  id: string;
  triggerTime: number;
  label?: string;
  enabled?: boolean;
}

interface AlarmResponse {
  success: boolean;
  message: string;
  alarmId?: string;
  scheduledTime?: number;
  alarmsJson?: string;
  testAlarmId?: string;
  testTime?: number;
}

interface PermissionResponse {
  canSchedule: boolean;
  message: string;
  androidVersion: number;
}

/**
 * Production-grade alarm manager for Android
 * Uses native AlarmManager with setExactAndAllowWhileIdle for reliability
 */
class ProductionAlarmManager {
  private nativeModule: any;

  constructor() {
    this.validatePlatform();
    this.nativeModule = NativeModules.AlarmModule;
  }

  /**
   * Validate platform and native module availability
   */
  private validatePlatform(): void {
    if (Platform.OS !== 'android') {
      throw new Error('AutoRise alarm functionality is only available on Android');
    }

    if (!NativeModules.AlarmModule) {
      throw new Error(
        'Native alarm module not available. ' +
        'This app requires a custom development build with native code. ' +
        'Please build with: npx expo run:android'
      );
    }
  }

  /**
   * Schedule a new alarm
   */
  async scheduleAlarm(alarmData: AlarmData): Promise<AlarmResponse> {
    try {
      this.validateAlarmData(alarmData);
      
      const response = await this.nativeModule.scheduleAlarm(alarmData);
      console.log('✅ Alarm scheduled successfully:', response);
      return response;
    } catch (error) {
      console.error('❌ Error scheduling alarm:', error);
      throw error;
    }
  }

  /**
   * Cancel an existing alarm
   */
  async cancelAlarm(alarmId: string): Promise<AlarmResponse> {
    try {
      if (!alarmId) {
        throw new Error('Alarm ID is required');
      }

      const response = await this.nativeModule.cancelAlarm(alarmId);
      console.log('✅ Alarm cancelled successfully:', response);
      return response;
    } catch (error) {
      console.error('❌ Error cancelling alarm:', error);
      throw error;
    }
  }

  /**
   * Get all scheduled alarms
   */
  async getAllAlarms(): Promise<AlarmData[]> {
    try {
      const response = await this.nativeModule.getAllAlarms();
      
      if (response.success && response.alarmsJson) {
        const alarms = JSON.parse(response.alarmsJson);
        console.log('✅ Retrieved alarms:', alarms);
        return alarms;
      }
      
      return [];
    } catch (error) {
      console.error('❌ Error getting alarms:', error);
      throw error;
    }
  }

  /**
   * Check if exact alarms can be scheduled
   */
  async canScheduleExactAlarms(): Promise<PermissionResponse> {
    try {
      const response = await this.nativeModule.canScheduleExactAlarms();
      console.log('✅ Permission check result:', response);
      return response;
    } catch (error) {
      console.error('❌ Error checking permissions:', error);
      throw error;
    }
  }

  /**
   * Request exact alarm permission (Android 12+)
   */
  async requestExactAlarmPermission(): Promise<string> {
    try {
      const response = await this.nativeModule.requestExactAlarmPermission();
      console.log('✅ Permission request sent:', response);
      return response;
    } catch (error) {
      console.error('❌ Error requesting permission:', error);
      throw error;
    }
  }

  /**
   * Test alarm functionality - schedules a test alarm for 10 seconds
   */
  async testAlarm(): Promise<AlarmResponse> {
    try {
      const response = await this.nativeModule.testAlarm();
      console.log('🧪 Test alarm scheduled:', response);
      return response;
    } catch (error) {
      console.error('❌ Error scheduling test alarm:', error);
      throw error;
    }
  }

  /**
   * Validate alarm data before scheduling
   */
  private validateAlarmData(alarmData: AlarmData): void {
    if (!alarmData.id) {
      throw new Error('Alarm ID is required');
    }

    if (!alarmData.triggerTime) {
      throw new Error('Trigger time is required');
    }

    if (alarmData.triggerTime <= Date.now()) {
      throw new Error('Alarm time must be in the future');
    }

    // Validate trigger time is not more than 1 year in the future
    const oneYearFromNow = Date.now() + (365 * 24 * 60 * 60 * 1000);
    if (alarmData.triggerTime > oneYearFromNow) {
      throw new Error('Alarm time cannot be more than 1 year in the future');
    }
  }

  /**
   * Create alarm data object for convenience
   */
  createAlarmData(id: string, triggerTime: number, label: string = 'Alarm'): AlarmData {
    return {
      id,
      triggerTime,
      label,
      enabled: true,
    };
  }

  /**
   * Schedule alarm for specific time (hour, minute)
   */
  async scheduleAlarmForTime(id: string, hour: number, minute: number, label: string = 'Alarm'): Promise<AlarmResponse> {
    // Calculate next occurrence of this time
    const now = new Date();
    const triggerTime = new Date();
    triggerTime.setHours(hour, minute, 0, 0);
    
    // If the time has passed today, schedule for tomorrow
    if (triggerTime <= now) {
      triggerTime.setDate(triggerTime.getDate() + 1);
    }

    const alarmData = this.createAlarmData(id, triggerTime.getTime(), label);
    return this.scheduleAlarm(alarmData);
  }

  /**
   * Format time for display
   */
  formatTime(timestamp: number): string {
    const date = new Date(timestamp);
    return date.toLocaleString();
  }

  /**
   * Check if app is ready for production alarms
   */
  async isReady(): Promise<boolean> {
    try {
      const permissionCheck = await this.canScheduleExactAlarms();
      return permissionCheck.canSchedule;
    } catch (error) {
      return false;
    }
  }
}

export default new ProductionAlarmManager();
export { AlarmData, AlarmResponse, PermissionResponse };
