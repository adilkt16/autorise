import { NativeModules, Platform } from 'react-native';
import type { AndroidAlarmManager } from './AndroidAlarmManager.d';

/**
 * Native Android Alarm Manager Bridge
 * Provides access to production-grade Android alarm functionality
 */

// Get the native module (will be null in development until native code is built)
const { AndroidAlarmManager } = NativeModules;

// Development fallback for when native module isn't available
const createFallbackModule = (): AndroidAlarmManager => ({
  scheduleAlarm: async (alarmId: string, hour: number, minute: number, alarmTime: string) => {
    console.warn('🚧 Native alarm module not available - using development fallback');
    console.log(`Would schedule alarm: ${alarmId} at ${alarmTime} (${hour}:${minute})`);
    return {
      alarmId,
      status: 'scheduled' as const,
      message: 'Development fallback - alarm scheduled locally'
    };
  },
  
  cancelAlarm: async (alarmId: string) => {
    console.warn('🚧 Native alarm module not available - using development fallback');
    console.log(`Would cancel alarm: ${alarmId}`);
    return {
      alarmId,
      status: 'canceled' as const,
      message: 'Development fallback - alarm canceled locally'
    };
  },
  
  canScheduleExactAlarms: async () => {
    console.warn('🚧 Native alarm module not available - using development fallback');
    return {
      canSchedule: Platform.OS === 'android',
      androidVersion: Platform.Version as number,
      message: 'Development fallback - permissions simulated'
    };
  },
  
  requestExactAlarmPermission: async () => {
    console.warn('🚧 Native alarm module not available - using development fallback');
    return 'Development fallback - permission request simulated';
  },
  
  stopAlarm: async () => {
    console.warn('🚧 Native alarm module not available - using development fallback');
    return 'Development fallback - alarm stopped locally';
  },
  
  requestBatteryOptimizationDisable: async () => {
    console.warn('🚧 Native alarm module not available - using development fallback');
    return 'Development fallback - battery optimization request simulated';
  }
});

// Use native module if available, otherwise use fallback
const AlarmManagerModule = AndroidAlarmManager || createFallbackModule();

/**
 * Production-grade Android alarm management
 * Uses AlarmManager.setExactAndAllowWhileIdle for reliable scheduling
 */
export class NativeAlarmManager {
  /**
   * Schedule a new alarm
   * @param alarmId Unique identifier for the alarm
   * @param hour Hour in 24-hour format (0-23)
   * @param minute Minute (0-59)
   * @param alarmTime Display string for the alarm time
   */
  static async scheduleAlarm(
    alarmId: string,
    hour: number,
    minute: number,
    alarmTime: string
  ) {
    try {
      console.log(`📱 Scheduling alarm: ${alarmId} at ${alarmTime}`);
      
      const result = await AlarmManagerModule.scheduleAlarm(
        alarmId,
        hour,
        minute,
        alarmTime
      );
      
      console.log('✅ Alarm scheduled:', result);
      return result;
    } catch (error) {
      console.error('❌ Failed to schedule alarm:', error);
      throw error;
    }
  };

  /**
   * Cancel an existing alarm
   * @param alarmId The alarm ID to cancel
   */
  static async cancelAlarm(alarmId: string) {
    try {
      console.log(`📱 Canceling alarm: ${alarmId}`);
      
      const result = await AlarmManagerModule.cancelAlarm(alarmId);
      
      console.log('✅ Alarm canceled:', result);
      return result;
    } catch (error) {
      console.error('❌ Failed to cancel alarm:', error);
      throw error;
    }
  };

  /**
   * Check if exact alarms can be scheduled
   */
  static async canScheduleExactAlarms() {
    try {
      const result = await AlarmManagerModule.canScheduleExactAlarms();
      console.log('📱 Exact alarm permission status:', result);
      return result;
    } catch (error) {
      console.error('❌ Failed to check alarm permissions:', error);
      throw error;
    }
  };

  /**
   * Request exact alarm permission (Android 12+)
   */
  static async requestExactAlarmPermission() {
    try {
      console.log('📱 Requesting exact alarm permission');
      const result = await AlarmManagerModule.requestExactAlarmPermission();
      console.log('✅ Permission request:', result);
      return result;
    } catch (error) {
      console.error('❌ Failed to request alarm permission:', error);
      throw error;
    }
  };

  /**
   * Stop any currently ringing alarm
   */
  static async stopAlarm() {
    try {
      console.log('📱 Stopping alarm');
      const result = await AlarmManagerModule.stopAlarm();
      console.log('✅ Alarm stop requested:', result);
      return result;
    } catch (error) {
      console.error('❌ Failed to stop alarm:', error);
      throw error;
    }
  };

  /**
   * Request battery optimization exclusion for reliable alarms
   */
  static async requestBatteryOptimizationDisable() {
    try {
      console.log('📱 Requesting battery optimization disable');
      const result = await AlarmManagerModule.requestBatteryOptimizationDisable();
      console.log('✅ Battery optimization request:', result);
      return result;
    } catch (error) {
      console.error('❌ Failed to request battery optimization disable:', error);
      throw error;
    }
  };

  /**
   * Initialize alarm permissions and settings
   * Call this when the app starts to ensure proper setup
   */
  static async initializeAlarmSystem() {
    try {
      console.log('📱 Initializing native alarm system');
      
      // Check if we can schedule exact alarms
      const permissionStatus = await this.canScheduleExactAlarms();
      
      if (!permissionStatus.canSchedule) {
        console.warn(
          '⚠️ Cannot schedule exact alarms. User needs to grant permission.',
          permissionStatus.message
        );
        return {
          success: false,
          needsPermission: true,
          message: permissionStatus.message,
          androidVersion: permissionStatus.androidVersion
        };
      }
      
      console.log('✅ Native alarm system ready');
      return {
        success: true,
        needsPermission: false,
        message: 'Alarm system initialized successfully',
        androidVersion: permissionStatus.androidVersion
      };
      
    } catch (error) {
      console.error('❌ Failed to initialize alarm system:', error);
      throw error;
    }
  }
}

export default NativeAlarmManager;
