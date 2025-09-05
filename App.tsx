import React, { useState, useEffect } from 'react';
import {
  StyleSheet,
  Text,
  View,
  TouchableOpacity,
  Alert,
  ScrollView,
  SafeAreaView,
  Platform,
  TextInput,
  Modal,
  Switch,
} from 'react-native';
import ProductionAlarmManager, { AlarmData } from './src/services/ProductionAlarmManager';

interface Alarm {
  id: string;
  hour: number;
  minute: number;
  label: string;
  enabled: boolean;
  triggerTime: number;
}

export default function App() {
  const [currentTime, setCurrentTime] = useState(new Date());
  const [alarms, setAlarms] = useState<Alarm[]>([]);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newAlarmHour, setNewAlarmHour] = useState('7');
  const [newAlarmMinute, setNewAlarmMinute] = useState('00');
  const [newAlarmLabel, setNewAlarmLabel] = useState('Wake up');
  const [isReady, setIsReady] = useState(false);
  const [permissionStatus, setPermissionStatus] = useState('Checking...');

  // Update current time every second
  useEffect(() => {
    const timer = setInterval(() => {
      setCurrentTime(new Date());
    }, 1000);

    return () => clearInterval(timer);
  }, []);

  // Initialize app and check permissions
  useEffect(() => {
    initializeApp();
  }, []);

  const initializeApp = async () => {
    try {
      // Check if native module is ready
      const ready = await ProductionAlarmManager.isReady();
      setIsReady(ready);

      // Check permissions
      const permissionCheck = await ProductionAlarmManager.canScheduleExactAlarms();
      setPermissionStatus(permissionCheck.message);

      // Load existing alarms
      await loadAlarms();

      console.log('📱 AutoRise initialized successfully');
    } catch (error: any) {
      console.error('❌ App initialization failed:', error);
      setPermissionStatus(`Error: ${error.message}`);
      setIsReady(false);
    }
  };

  const loadAlarms = async () => {
    try {
      const loadedAlarms = await ProductionAlarmManager.getAllAlarms();
      const formattedAlarms = loadedAlarms.map(alarm => ({
        id: alarm.id,
        hour: new Date(alarm.triggerTime).getHours(),
        minute: new Date(alarm.triggerTime).getMinutes(),
        label: alarm.label || 'Alarm',
        enabled: alarm.enabled ?? true,
        triggerTime: alarm.triggerTime,
      }));
      setAlarms(formattedAlarms);
      console.log('✅ Loaded alarms:', formattedAlarms);
    } catch (error) {
      console.log('ℹ️ No alarms to load or native module not available');
    }
  };

  const createAlarm = async () => {
    try {
      const hour = parseInt(newAlarmHour);
      const minute = parseInt(newAlarmMinute);
      
      if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
        Alert.alert('Invalid Time', 'Please enter a valid time (hour: 0-23, minute: 0-59)');
        return;
      }

      const response = await ProductionAlarmManager.scheduleAlarmForTime(
        `alarm-${Date.now()}`,
        hour,
        minute,
        newAlarmLabel
      );
      
      if (response.success) {
        await loadAlarms(); // Reload to get latest data
        setShowCreateModal(false);
        resetForm();
        
        Alert.alert(
          '✅ Alarm Created',
          `Alarm "${newAlarmLabel}" set for ${formatTime(hour, minute)}\n\n` +
          `The alarm will play reliably even when your device is locked or in Doze mode.`
        );
      }
    } catch (error: any) {
      Alert.alert(
        '❌ Error Creating Alarm',
        error.message || 'Failed to create alarm. Make sure you have granted exact alarm permissions.'
      );
    }
  };

  const deleteAlarm = async (alarmId: string) => {
    try {
      await ProductionAlarmManager.cancelAlarm(alarmId);
      await loadAlarms();
      Alert.alert('✅ Alarm Deleted', 'Alarm has been cancelled and removed');
    } catch (error: any) {
      Alert.alert('❌ Error', error.message || 'Failed to delete alarm');
    }
  };

  const testAlarm = async () => {
    try {
      const response = await ProductionAlarmManager.testAlarm();
      if (response.success) {
        Alert.alert(
          '🧪 Test Alarm Scheduled',
          'A test alarm will ring in 10 seconds.\n\n' +
          '🔊 Make sure your device volume is up!\n' +
          '📱 You can lock your device - the alarm will still play.\n' +
          '⏰ This tests the production-grade alarm system.'
        );
      }
    } catch (error: any) {
      Alert.alert(
        '❌ Test Failed',
        error.message || 'Native alarm module not available. This requires a custom development build.'
      );
    }
  };

  const requestPermissions = async () => {
    try {
      await ProductionAlarmManager.requestExactAlarmPermission();
      Alert.alert(
        'Permission Request',
        'Opening system settings. Please grant "Alarms & reminders" permission for reliable alarm functionality.'
      );
      
      // Recheck permissions after a delay
      setTimeout(async () => {
        const permissionCheck = await ProductionAlarmManager.canScheduleExactAlarms();
        setPermissionStatus(permissionCheck.message);
        setIsReady(permissionCheck.canSchedule);
      }, 3000);
    } catch (error: any) {
      Alert.alert('Error', error.message || 'Failed to request permissions');
    }
  };

  const resetForm = () => {
    setNewAlarmHour('7');
    setNewAlarmMinute('00');
    setNewAlarmLabel('Wake up');
  };

  const formatTime = (hour: number, minute: number): string => {
    const h = hour === 0 ? 12 : hour > 12 ? hour - 12 : hour;
    const ampm = hour >= 12 ? 'PM' : 'AM';
    return `${h}:${minute.toString().padStart(2, '0')} ${ampm}`;
  };

  const formatDateTime = (timestamp: number): string => {
    return new Date(timestamp).toLocaleString();
  };

  return (
    <SafeAreaView style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.title}>⏰ AutoRise</Text>
        <Text style={styles.subtitle}>Production-Grade Android Alarm</Text>
        <Text style={styles.currentTime}>
          {currentTime.toLocaleTimeString([], { 
            hour: '2-digit', 
            minute: '2-digit',
            second: '2-digit',
            hour12: true 
          })}
        </Text>
      </View>

      <ScrollView style={styles.content}>
        {/* System Status */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>System Status</Text>
          <View style={styles.statusCard}>
            <Text style={styles.statusText}>
              Native Module: {isReady ? '✅ Ready' : '❌ Not Available'}
            </Text>
            <Text style={styles.statusText}>Platform: {Platform.OS} {Platform.Version}</Text>
            <Text style={styles.statusText}>Permissions: {permissionStatus}</Text>
            
            {!isReady && (
              <TouchableOpacity style={styles.permissionButton} onPress={requestPermissions}>
                <Text style={styles.permissionButtonText}>Grant Permissions</Text>
              </TouchableOpacity>
            )}
          </View>
        </View>

        {/* Active Alarms */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Active Alarms ({alarms.length})</Text>
          {alarms.length === 0 ? (
            <View style={styles.emptyState}>
              <Text style={styles.emptyText}>No alarms set</Text>
              <Text style={styles.emptySubtext}>
                Create your first production-grade alarm that works even when device is locked
              </Text>
            </View>
          ) : (
            alarms.map(alarm => (
              <View key={alarm.id} style={styles.alarmCard}>
                <View style={styles.alarmInfo}>
                  <Text style={styles.alarmTime}>
                    {formatTime(alarm.hour, alarm.minute)}
                  </Text>
                  <Text style={styles.alarmLabel}>{alarm.label}</Text>
                  <Text style={styles.alarmScheduled}>
                    Next: {formatDateTime(alarm.triggerTime)}
                  </Text>
                </View>
                <TouchableOpacity
                  style={styles.deleteButton}
                  onPress={() => deleteAlarm(alarm.id)}
                >
                  <Text style={styles.deleteButtonText}>Cancel</Text>
                </TouchableOpacity>
              </View>
            ))
          )}
        </View>

        {/* Test Section */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Test & Debug</Text>
          <TouchableOpacity 
            style={[styles.testButton, !isReady && styles.disabledButton]} 
            onPress={testAlarm}
            disabled={!isReady}
          >
            <Text style={styles.testButtonText}>🧪 Test Alarm (10 seconds)</Text>
          </TouchableOpacity>
          <Text style={styles.testInfo}>
            Tests production alarm functionality. Works even when device is locked or in Doze mode.
            {!isReady && '\n\n⚠️ Requires native module and permissions.'}
          </Text>
        </View>

        {/* Technical Info */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Technical Features</Text>
          <Text style={styles.featureText}>✅ AlarmManager.setExactAndAllowWhileIdle</Text>
          <Text style={styles.featureText}>✅ Foreground service for audio playback</Text>
          <Text style={styles.featureText}>✅ USAGE_ALARM (bypasses Do Not Disturb)</Text>
          <Text style={styles.featureText}>✅ Wake locks and screen management</Text>
          <Text style={styles.featureText}>✅ Full-screen alarm over lock screen</Text>
          <Text style={styles.featureText}>✅ Survives device restart</Text>
        </View>
      </ScrollView>

      {/* Add Alarm Button */}
      <TouchableOpacity
        style={[styles.addButton, !isReady && styles.disabledButton]}
        onPress={() => setShowCreateModal(true)}
        disabled={!isReady}
      >
        <Text style={styles.addButtonText}>+ Create Alarm</Text>
      </TouchableOpacity>

      {/* Create Alarm Modal */}
      <Modal
        visible={showCreateModal}
        animationType="slide"
        transparent={true}
        onRequestClose={() => setShowCreateModal(false)}
      >
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <Text style={styles.modalTitle}>Create Production Alarm</Text>
            
            <View style={styles.timeInputContainer}>
              <View style={styles.timeInput}>
                <Text style={styles.inputLabel}>Hour (0-23)</Text>
                <TextInput
                  style={styles.textInput}
                  value={newAlarmHour}
                  onChangeText={setNewAlarmHour}
                  keyboardType="numeric"
                  maxLength={2}
                />
              </View>
              <Text style={styles.timeSeparator}>:</Text>
              <View style={styles.timeInput}>
                <Text style={styles.inputLabel}>Minute (0-59)</Text>
                <TextInput
                  style={styles.textInput}
                  value={newAlarmMinute}
                  onChangeText={setNewAlarmMinute}
                  keyboardType="numeric"
                  maxLength={2}
                />
              </View>
            </View>

            <View style={styles.labelInputContainer}>
              <Text style={styles.inputLabel}>Alarm Label</Text>
              <TextInput
                style={styles.textInput}
                value={newAlarmLabel}
                onChangeText={setNewAlarmLabel}
                placeholder="Enter alarm description"
                maxLength={50}
              />
            </View>

            <Text style={styles.modalInfo}>
              This alarm will use native Android AlarmManager for maximum reliability.
              It will ring even when your device is locked or in Doze mode.
            </Text>

            <View style={styles.modalButtons}>
              <TouchableOpacity
                style={[styles.modalButton, styles.cancelButton]}
                onPress={() => setShowCreateModal(false)}
              >
                <Text style={styles.cancelButtonText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.modalButton, styles.createButton]}
                onPress={createAlarm}
              >
                <Text style={styles.createButtonText}>Create Alarm</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f8f9fa',
  },
  header: {
    backgroundColor: '#1976D2',
    padding: 20,
    alignItems: 'center',
  },
  title: {
    fontSize: 32,
    fontWeight: 'bold',
    color: 'white',
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 14,
    color: '#E3F2FD',
    marginBottom: 8,
  },
  currentTime: {
    fontSize: 20,
    color: 'white',
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace',
    fontWeight: 'bold',
  },
  content: {
    flex: 1,
    padding: 16,
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 12,
  },
  statusCard: {
    backgroundColor: 'white',
    borderRadius: 8,
    padding: 16,
    borderWidth: 1,
    borderColor: '#e0e0e0',
  },
  statusText: {
    fontSize: 14,
    color: '#666',
    marginBottom: 4,
  },
  permissionButton: {
    backgroundColor: '#FF9800',
    padding: 12,
    borderRadius: 6,
    alignItems: 'center',
    marginTop: 8,
  },
  permissionButtonText: {
    color: 'white',
    fontWeight: 'bold',
  },
  emptyState: {
    alignItems: 'center',
    padding: 32,
    backgroundColor: 'white',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#e0e0e0',
  },
  emptyText: {
    fontSize: 18,
    color: '#666',
    marginBottom: 8,
    fontWeight: 'bold',
  },
  emptySubtext: {
    fontSize: 14,
    color: '#999',
    textAlign: 'center',
    lineHeight: 20,
  },
  alarmCard: {
    backgroundColor: 'white',
    borderRadius: 8,
    padding: 16,
    marginBottom: 8,
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#e0e0e0',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 2,
  },
  alarmInfo: {
    flex: 1,
  },
  alarmTime: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#1976D2',
    marginBottom: 4,
  },
  alarmLabel: {
    fontSize: 16,
    color: '#333',
    marginBottom: 4,
  },
  alarmScheduled: {
    fontSize: 12,
    color: '#666',
  },
  deleteButton: {
    backgroundColor: '#f44336',
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 4,
  },
  deleteButtonText: {
    color: 'white',
    fontWeight: 'bold',
  },
  testButton: {
    backgroundColor: '#4CAF50',
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
    marginBottom: 8,
  },
  testButtonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: 'bold',
  },
  testInfo: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
    fontStyle: 'italic',
    lineHeight: 20,
  },
  featureText: {
    fontSize: 14,
    color: '#4CAF50',
    marginBottom: 4,
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace',
  },
  addButton: {
    backgroundColor: '#1976D2',
    margin: 16,
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
  },
  addButtonText: {
    color: 'white',
    fontSize: 18,
    fontWeight: 'bold',
  },
  disabledButton: {
    backgroundColor: '#ccc',
    opacity: 0.6,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalContent: {
    backgroundColor: 'white',
    borderRadius: 12,
    padding: 24,
    width: '90%',
    maxWidth: 400,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 8,
  },
  modalTitle: {
    fontSize: 22,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 24,
    color: '#333',
  },
  timeInputContainer: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'center',
    marginBottom: 20,
  },
  timeInput: {
    alignItems: 'center',
  },
  timeSeparator: {
    fontSize: 32,
    fontWeight: 'bold',
    marginHorizontal: 12,
    marginBottom: 12,
    color: '#1976D2',
  },
  inputLabel: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 8,
  },
  textInput: {
    borderWidth: 2,
    borderColor: '#1976D2',
    borderRadius: 8,
    padding: 12,
    fontSize: 18,
    textAlign: 'center',
    minWidth: 80,
    fontWeight: 'bold',
  },
  labelInputContainer: {
    marginBottom: 20,
  },
  modalInfo: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
    marginBottom: 24,
    lineHeight: 20,
    fontStyle: 'italic',
  },
  modalButtons: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  modalButton: {
    flex: 1,
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
  },
  cancelButton: {
    backgroundColor: '#f5f5f5',
    marginRight: 8,
  },
  createButton: {
    backgroundColor: '#1976D2',
    marginLeft: 8,
  },
  cancelButtonText: {
    color: '#666',
    fontWeight: 'bold',
  },
  createButtonText: {
    color: 'white',
    fontWeight: 'bold',
  },
});
