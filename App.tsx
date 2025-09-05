import React, { useState, useEffect, useRef } from 'react';
import { 
  View, 
  Text, 
  TouchableOpacity, 
  StyleSheet, 
  SafeAreaView, 
  ScrollView, 
  Modal, 
  TextInput, 
  Alert,
  AppState,
  Platform,
  Vibration,
  Dimensions
} from 'react-native';
import { Audio } from 'expo-av';
import { activateKeepAwake, deactivateKeepAwake } from 'expo-keep-awake';
import NativeAlarmManager from './src/native/NativeAlarmManager';

interface Alarm {
  id: string;
  hour: number;
  minute: number;
  ampm: 'AM' | 'PM';
  isActive: boolean;
}

export default function App() {
  const [currentTime, setCurrentTime] = useState(new Date());
  const [alarms, setAlarms] = useState<Alarm[]>([]);
  const [showCreateAlarm, setShowCreateAlarm] = useState(false);
  const [newAlarmHour, setNewAlarmHour] = useState('12');
  const [newAlarmMinute, setNewAlarmMinute] = useState('00');
  const [newAlarmAmPm, setNewAlarmAmPm] = useState<'AM' | 'PM'>('AM');
  const [isAlarmRinging, setIsAlarmRinging] = useState(false);
  const [currentRingingAlarm, setCurrentRingingAlarm] = useState<Alarm | null>(null);
  const [alarmSound, setAlarmSound] = useState<Audio.Sound | null>(null);
  const [appState, setAppState] = useState(AppState.currentState);
  const [nativeAlarmReady, setNativeAlarmReady] = useState(false);
  const [permissionStatus, setPermissionStatus] = useState<string>('');
  const alarmTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  // Setup native alarm system on app start
  useEffect(() => {
    setupAudio();
    initializeNativeAlarmSystem();

    // Listen for app state changes
    const subscription = AppState.addEventListener('change', handleAppStateChange);

    return () => {
      subscription?.remove();
      if (alarmTimeoutRef.current) {
        clearTimeout(alarmTimeoutRef.current);
      }
    };
  }, []);

  // Timer to update current time display and check for alarms
  useEffect(() => {
    const timer = setInterval(() => {
      const now = new Date();
      setCurrentTime(now);
      checkAlarms(now); // Check if any alarms should trigger
    }, 1000);

    return () => clearInterval(timer);
  }, [alarms]); // Re-run when alarms change

  // Initialize the native Android alarm system
  const initializeNativeAlarmSystem = async () => {
    try {
      console.log('🚀 Initializing native Android alarm system...');
      
      const result = await NativeAlarmManager.initializeAlarmSystem();
      
      if (result.success) {
        setNativeAlarmReady(true);
        setPermissionStatus('✅ Alarm system ready');
        console.log('✅ Native alarm system initialized successfully');
      } else if (result.needsPermission) {
        setNativeAlarmReady(false);
        setPermissionStatus(`⚠️ Need permission: ${result.message}`);
        console.warn('⚠️ Alarm system needs permission:', result.message);
        
        // Show permission request dialog
        Alert.alert(
          'Alarm Permission Required',
          `This app needs permission to schedule exact alarms on Android ${result.androidVersion}+. ` +
          'Without this permission, alarms may not ring reliably.',
          [
            { text: 'Cancel', style: 'cancel' },
            { 
              text: 'Grant Permission', 
              onPress: () => requestAlarmPermission() 
            }
          ]
        );
      }
    } catch (error) {
      console.error('❌ Failed to initialize native alarm system:', error);
      setPermissionStatus('❌ Alarm system failed to initialize');
      setNativeAlarmReady(false);
    }
  };

  // Request exact alarm permission for Android 12+
  const requestAlarmPermission = async () => {
    try {
      await NativeAlarmManager.requestExactAlarmPermission();
      
      // Re-check permission status after user returns from settings
      setTimeout(async () => {
        const result = await NativeAlarmManager.canScheduleExactAlarms();
        if (result.canSchedule) {
          setNativeAlarmReady(true);
          setPermissionStatus('✅ Alarm system ready');
          Alert.alert('Success', 'Alarm permission granted! You can now create reliable alarms.');
        } else {
          setPermissionStatus('⚠️ Permission still needed');
        }
      }, 1000);
    } catch (error) {
      console.error('❌ Failed to request alarm permission:', error);
      Alert.alert('Error', 'Failed to request alarm permission');
    }
  };

  // Handle changes in app state (foreground/background)
  const handleAppStateChange = (nextAppState: any) => {
    console.log('App state changed from', appState, 'to', nextAppState);
    setAppState(nextAppState);
  };

  const setupAudio = async () => {
    try {
      await Audio.setAudioModeAsync({
        staysActiveInBackground: true,
        shouldDuckAndroid: false,
        playThroughEarpieceAndroid: false,
      });
    } catch (error) {
      console.error('Error setting up audio:', error);
    }
  };

  // Check if any alarms should trigger based on current time
  const checkAlarms = (currentTime: Date) => {
    const currentHour = currentTime.getHours();
    const currentMinute = currentTime.getMinutes();
    const currentSecond = currentTime.getSeconds();

    // Only check at the start of each minute (when seconds = 0)
    if (currentSecond !== 0) return;

    alarms.forEach(alarm => {
      if (alarm.isActive && alarm.hour === currentHour && alarm.minute === currentMinute) {
        console.log(`🚨 Alarm triggered: ${alarm.id} at ${currentHour}:${currentMinute}`);
        triggerAlarm(alarm);
      }
    });
  };

  // Trigger an alarm (play audio, show UI, vibrate)
  const triggerAlarm = async (alarm: Alarm) => {
    try {
      console.log('🔔 Triggering alarm:', alarm);
      
      // Set alarm ringing state
      setIsAlarmRinging(true);
      setCurrentRingingAlarm(alarm);
      
      // Activate keep awake to prevent screen sleep
      activateKeepAwake();
      
      // Start vibration pattern
      const vibrationPattern = [0, 1000, 500, 1000, 500, 1000, 500];
      Vibration.vibrate(vibrationPattern, true); // Repeat until stopped
      
      // Play alarm audio
      await playAlarmSound();
      
      console.log('🎵 Alarm is now ringing with audio and vibration');
    } catch (error) {
      console.error('❌ Failed to trigger alarm:', error);
    }
  };

  // Play the alarm sound
  const playAlarmSound = async () => {
    try {
      console.log('🎵 Starting alarm audio...');
      
      // Stop any existing sound first
      if (alarmSound) {
        await alarmSound.stopAsync();
        await alarmSound.unloadAsync();
        setAlarmSound(null);
      }

      // Create and play new alarm sound
      const { sound } = await Audio.Sound.createAsync(
        require('./assets/alarm_default.mp3'),
        { 
          shouldPlay: true, 
          isLooping: true, // Loop continuously until dismissed
          volume: 1.0,
        }
      );

      setAlarmSound(sound);
      console.log('🔊 Alarm audio playing (looping)');
      
    } catch (error) {
      console.error('❌ Failed to play alarm sound:', error);
    }
  };

  // Create a new alarm using native Android AlarmManager
  const createAlarm = async () => {
    if (!nativeAlarmReady) {
      Alert.alert(
        'Alarm System Not Ready',
        'Please grant alarm permissions first. Check settings.'
      );
      return;
    }

    try {
      const hour = parseInt(newAlarmHour);
      const minute = parseInt(newAlarmMinute);
      
      // Convert to 24-hour format for native alarm
      let hour24 = hour;
      if (newAlarmAmPm === 'PM' && hour !== 12) {
        hour24 += 12;
      } else if (newAlarmAmPm === 'AM' && hour === 12) {
        hour24 = 0;
      }

      const alarmId = `alarm_${Date.now()}`;
      const alarmTime = `${newAlarmHour}:${newAlarmMinute.padStart(2, '0')} ${newAlarmAmPm}`;

      // Create the alarm object
      const newAlarm: Alarm = {
        id: alarmId,
        hour: hour24,
        minute,
        ampm: newAlarmAmPm,
        isActive: true,
      };

      console.log('📱 Creating native alarm:', alarmTime, `(${hour24}:${minute})`);
      
      // Schedule with native Android AlarmManager
      const result = await NativeAlarmManager.scheduleAlarm(
        alarmId,
        hour24,
        minute,
        alarmTime
      );

      // Add to local state
      setAlarms([...alarms, newAlarm]);
      
      // Reset form
      setNewAlarmHour('12');
      setNewAlarmMinute('00');
      setNewAlarmAmPm('AM');
      setShowCreateAlarm(false);

      Alert.alert(
        'Native Alarm Scheduled',
        `✅ Alarm set for ${alarmTime}\n\nUsing Android AlarmManager for reliable wake-up. ` +
        'This alarm will ring even when the device is locked or idle.',
        [{ text: 'OK' }]
      );

      console.log('✅ Native alarm created successfully:', result);
    } catch (error) {
      console.error('❌ Failed to create native alarm:', error);
      Alert.alert('Error', `Failed to create alarm: ${error}`);
    }
  };

  // Toggle alarm on/off
  const toggleAlarm = async (alarmId: string) => {
    try {
      const alarm = alarms.find(a => a.id === alarmId);
      if (!alarm) return;

      if (alarm.isActive) {
        // Cancel the native alarm
        await NativeAlarmManager.cancelAlarm(alarmId);
        console.log('🚫 Native alarm canceled:', alarmId);
      } else {
        // Re-schedule the native alarm
        const hour24 = alarm.hour;
        const alarmTime = `${alarm.hour === 0 ? 12 : alarm.hour > 12 ? alarm.hour - 12 : alarm.hour}:${alarm.minute.toString().padStart(2, '0')} ${alarm.ampm}`;
        
        await NativeAlarmManager.scheduleAlarm(
          alarmId,
          hour24,
          alarm.minute,
          alarmTime
        );
        console.log('✅ Native alarm re-scheduled:', alarmId);
      }

      // Update local state
      setAlarms(alarms.map(a => 
        a.id === alarmId ? { ...a, isActive: !a.isActive } : a
      ));
    } catch (error) {
      console.error('❌ Failed to toggle alarm:', error);
      Alert.alert('Error', `Failed to toggle alarm: ${error}`);
    }
  };

  // Delete an alarm
  const deleteAlarm = async (alarmId: string) => {
    try {
      // Cancel the native alarm
      await NativeAlarmManager.cancelAlarm(alarmId);
      
      // Remove from local state
      setAlarms(alarms.filter(a => a.id !== alarmId));
      
      console.log('🗑️ Native alarm deleted:', alarmId);
    } catch (error) {
      console.error('❌ Failed to delete alarm:', error);
      Alert.alert('Error', `Failed to delete alarm: ${error}`);
    }
  };

  // Stop currently ringing alarm
  const dismissAlarm = async () => {
    try {
      console.log('📱 Dismissing alarm...');
      
      // Stop the native alarm service
      await NativeAlarmManager.stopAlarm();
      
      // Stop local audio if playing
      if (alarmSound) {
        await alarmSound.stopAsync();
        await alarmSound.unloadAsync();
        setAlarmSound(null);
      }

      // Stop vibration
      Vibration.cancel();
      
      // Deactivate keep awake
      deactivateKeepAwake();
      
      // Reset alarm state
      setIsAlarmRinging(false);
      setCurrentRingingAlarm(null);
      
      if (alarmTimeoutRef.current) {
        clearTimeout(alarmTimeoutRef.current);
        alarmTimeoutRef.current = null;
      }

      console.log('✅ Alarm dismissed successfully');
    } catch (error) {
      console.error('❌ Failed to dismiss alarm:', error);
    }
  };

  // Format time for display
  const formatTime = (date: Date) => {
    return date.toLocaleTimeString('en-US', {
      hour12: true,
      hour: 'numeric',
      minute: '2-digit',
      second: '2-digit'
    });
  };

  // Format alarm time for display
  const formatAlarmTime = (alarm: Alarm) => {
    const displayHour = alarm.hour === 0 ? 12 : alarm.hour > 12 ? alarm.hour - 12 : alarm.hour;
    return `${displayHour}:${alarm.minute.toString().padStart(2, '0')} ${alarm.ampm}`;
  };

  // Request battery optimization settings
  const requestBatteryOptimization = async () => {
    try {
      await NativeAlarmManager.requestBatteryOptimizationDisable();
    } catch (error) {
      console.error('❌ Failed to request battery optimization:', error);
    }
  };

  // Test alarm functionality (development only)
  const testAlarm = async () => {
    const testAlarmData: Alarm = {
      id: 'test_alarm',
      hour: new Date().getHours(),
      minute: new Date().getMinutes(),
      ampm: new Date().getHours() >= 12 ? 'PM' : 'AM',
      isActive: true
    };
    
    console.log('🧪 Testing alarm functionality...');
    await triggerAlarm(testAlarmData);
  };

  return (
    <SafeAreaView style={styles.container}>
      {/* Status Bar */}
      <View style={styles.statusBar}>
        <Text style={styles.statusText}>{permissionStatus}</Text>
        {!nativeAlarmReady && (
          <TouchableOpacity 
            style={styles.permissionButton}
            onPress={requestAlarmPermission}
          >
            <Text style={styles.permissionButtonText}>Grant Permission</Text>
          </TouchableOpacity>
        )}
      </View>

      {/* Current Time Display */}
      <View style={styles.timeContainer}>
        <Text style={styles.currentTime}>{formatTime(currentTime)}</Text>
        <Text style={styles.nativeLabel}>Native Android Alarms</Text>
      </View>

      {/* Battery Optimization Notice */}
      <TouchableOpacity 
        style={styles.batteryButton}
        onPress={requestBatteryOptimization}
      >
        <Text style={styles.batteryButtonText}>
          🔋 Disable Battery Optimization for Reliable Alarms
        </Text>
      </TouchableOpacity>

      {/* Test Alarm Button (Development) */}
      <TouchableOpacity 
        style={styles.testButton}
        onPress={testAlarm}
      >
        <Text style={styles.testButtonText}>
          🧪 Test Alarm (Audio + Vibration)
        </Text>
      </TouchableOpacity>

      {/* Alarms List */}
      <ScrollView style={styles.alarmsContainer}>
        {alarms.length === 0 ? (
          <Text style={styles.noAlarmsText}>
            No alarms set. Create your first native alarm below.
          </Text>
        ) : (
          alarms.map((alarm) => (
            <View key={alarm.id} style={styles.alarmItem}>
              <View style={styles.alarmInfo}>
                <Text style={styles.alarmTime}>{formatAlarmTime(alarm)}</Text>
                <Text style={styles.alarmType}>Native Android AlarmManager</Text>
              </View>
              <View style={styles.alarmControls}>
                <TouchableOpacity
                  style={[
                    styles.toggleButton,
                    { backgroundColor: alarm.isActive ? '#4CAF50' : '#9E9E9E' }
                  ]}
                  onPress={() => toggleAlarm(alarm.id)}
                >
                  <Text style={styles.toggleButtonText}>
                    {alarm.isActive ? 'ON' : 'OFF'}
                  </Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={styles.deleteButton}
                  onPress={() => deleteAlarm(alarm.id)}
                >
                  <Text style={styles.deleteButtonText}>🗑️</Text>
                </TouchableOpacity>
              </View>
            </View>
          ))
        )}
      </ScrollView>

      {/* Add Alarm Button */}
      <TouchableOpacity
        style={[
          styles.addButton,
          { backgroundColor: nativeAlarmReady ? '#2196F3' : '#9E9E9E' }
        ]}
        onPress={() => setShowCreateAlarm(true)}
        disabled={!nativeAlarmReady}
      >
        <Text style={styles.addButtonText}>+ Add Native Alarm</Text>
      </TouchableOpacity>

      {/* Create Alarm Modal */}
      <Modal
        visible={showCreateAlarm}
        animationType="slide"
        transparent={true}
      >
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <Text style={styles.modalTitle}>Create Native Alarm</Text>
            <Text style={styles.modalSubtitle}>Using Android AlarmManager</Text>
            
            <View style={styles.timeInputContainer}>
              <TextInput
                style={styles.timeInput}
                value={newAlarmHour}
                onChangeText={setNewAlarmHour}
                placeholder="12"
                keyboardType="numeric"
                maxLength={2}
              />
              <Text style={styles.timeSeparator}>:</Text>
              <TextInput
                style={styles.timeInput}
                value={newAlarmMinute}
                onChangeText={setNewAlarmMinute}
                placeholder="00"
                keyboardType="numeric"
                maxLength={2}
              />
              <TouchableOpacity
                style={styles.ampmButton}
                onPress={() => setNewAlarmAmPm(newAlarmAmPm === 'AM' ? 'PM' : 'AM')}
              >
                <Text style={styles.ampmText}>{newAlarmAmPm}</Text>
              </TouchableOpacity>
            </View>

            <View style={styles.modalButtons}>
              <TouchableOpacity
                style={styles.cancelButton}
                onPress={() => setShowCreateAlarm(false)}
              >
                <Text style={styles.cancelButtonText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={styles.createButton}
                onPress={createAlarm}
              >
                <Text style={styles.createButtonText}>Create</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>

      {/* Alarm Ringing Modal */}
      <Modal
        visible={isAlarmRinging}
        animationType="fade"
        transparent={false}
      >
        <View style={styles.alarmRingingContainer}>
          <Text style={styles.alarmRingingTitle}>⏰ ALARM</Text>
          <Text style={styles.alarmRingingTime}>
            {currentRingingAlarm ? formatAlarmTime(currentRingingAlarm) : ''}
          </Text>
          <Text style={styles.alarmRingingSubtitle}>Native Android Alarm</Text>
          
          <TouchableOpacity
            style={styles.dismissButton}
            onPress={dismissAlarm}
          >
            <Text style={styles.dismissButtonText}>DISMISS</Text>
          </TouchableOpacity>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
  },
  statusBar: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    backgroundColor: '#1a1a1a',
  },
  statusText: {
    color: '#fff',
    fontSize: 14,
    flex: 1,
  },
  permissionButton: {
    backgroundColor: '#FF9800',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 6,
  },
  permissionButtonText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: 'bold',
  },
  timeContainer: {
    alignItems: 'center',
    padding: 30,
  },
  currentTime: {
    fontSize: 48,
    color: '#fff',
    fontWeight: 'bold',
  },
  nativeLabel: {
    fontSize: 16,
    color: '#4CAF50',
    marginTop: 8,
    fontWeight: 'bold',
  },
  batteryButton: {
    backgroundColor: '#FF5722',
    margin: 16,
    padding: 12,
    borderRadius: 8,
  },
  batteryButtonText: {
    color: '#fff',
    textAlign: 'center',
    fontSize: 14,
    fontWeight: 'bold',
  },
  testButton: {
    backgroundColor: '#9C27B0',
    marginHorizontal: 16,
    marginBottom: 16,
    padding: 12,
    borderRadius: 8,
  },
  testButtonText: {
    color: '#fff',
    textAlign: 'center',
    fontSize: 14,
    fontWeight: 'bold',
  },
  alarmsContainer: {
    flex: 1,
    padding: 16,
  },
  noAlarmsText: {
    color: '#666',
    textAlign: 'center',
    fontSize: 16,
    marginTop: 40,
  },
  alarmItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: '#1a1a1a',
    padding: 16,
    borderRadius: 8,
    marginBottom: 8,
  },
  alarmInfo: {
    flex: 1,
  },
  alarmTime: {
    color: '#fff',
    fontSize: 24,
    fontWeight: 'bold',
  },
  alarmType: {
    color: '#4CAF50',
    fontSize: 12,
    marginTop: 4,
  },
  alarmControls: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  toggleButton: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 6,
    marginRight: 8,
  },
  toggleButtonText: {
    color: '#fff',
    fontWeight: 'bold',
  },
  deleteButton: {
    padding: 8,
  },
  deleteButtonText: {
    fontSize: 18,
  },
  addButton: {
    margin: 16,
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
  },
  addButtonText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: 'bold',
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.8)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalContent: {
    backgroundColor: '#1a1a1a',
    padding: 24,
    borderRadius: 12,
    width: '90%',
  },
  modalTitle: {
    color: '#fff',
    fontSize: 24,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 4,
  },
  modalSubtitle: {
    color: '#4CAF50',
    fontSize: 14,
    textAlign: 'center',
    marginBottom: 24,
  },
  timeInputContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 24,
  },
  timeInput: {
    backgroundColor: '#333',
    color: '#fff',
    padding: 12,
    borderRadius: 8,
    fontSize: 24,
    textAlign: 'center',
    width: 60,
  },
  timeSeparator: {
    color: '#fff',
    fontSize: 24,
    marginHorizontal: 8,
  },
  ampmButton: {
    backgroundColor: '#2196F3',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 8,
    marginLeft: 16,
  },
  ampmText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: 'bold',
  },
  modalButtons: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  cancelButton: {
    backgroundColor: '#666',
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
    flex: 1,
    marginRight: 8,
  },
  cancelButtonText: {
    color: '#fff',
    textAlign: 'center',
    fontSize: 16,
    fontWeight: 'bold',
  },
  createButton: {
    backgroundColor: '#4CAF50',
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
    flex: 1,
    marginLeft: 8,
  },
  createButtonText: {
    color: '#fff',
    textAlign: 'center',
    fontSize: 16,
    fontWeight: 'bold',
  },
  alarmRingingContainer: {
    flex: 1,
    backgroundColor: '#FF5722',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 32,
  },
  alarmRingingTitle: {
    fontSize: 64,
    color: '#fff',
    fontWeight: 'bold',
    marginBottom: 16,
  },
  alarmRingingTime: {
    fontSize: 48,
    color: '#fff',
    fontWeight: 'bold',
    marginBottom: 8,
  },
  alarmRingingSubtitle: {
    fontSize: 18,
    color: '#fff',
    marginBottom: 48,
  },
  dismissButton: {
    backgroundColor: '#fff',
    paddingHorizontal: 48,
    paddingVertical: 16,
    borderRadius: 12,
  },
  dismissButtonText: {
    color: '#FF5722',
    fontSize: 24,
    fontWeight: 'bold',
  },
});