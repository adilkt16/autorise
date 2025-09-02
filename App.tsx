import { StyleSheet, Text, View, SafeAreaView, TouchableOpacity, Alert, Modal, TextInput } from 'react-native';
import { Audio } from 'expo-av';
import { useState, useEffect } from 'react';

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

  useEffect(() => {
    const timer = setInterval(() => {
      const now = new Date();
      setCurrentTime(now);
      checkAlarms(now);
    }, 1000);

    return () => clearInterval(timer);
  }, [alarms]);

  const checkAlarms = (now: Date) => {
    const currentHour = now.getHours();
    const currentMinute = now.getMinutes();
    const currentSecond = now.getSeconds();

    // Only check at the start of each minute (when seconds = 0)
    if (currentSecond !== 0) return;

    alarms.forEach(alarm => {
      if (!alarm.isActive) return;

      // Convert alarm time to 24-hour format
      let alarmHour24 = alarm.hour;
      if (alarm.ampm === 'PM' && alarm.hour !== 12) {
        alarmHour24 += 12;
      } else if (alarm.ampm === 'AM' && alarm.hour === 12) {
        alarmHour24 = 0;
      }

      if (currentHour === alarmHour24 && currentMinute === alarm.minute) {
        triggerAlarm(alarm);
      }
    });
  };

  const triggerAlarm = async (alarm: Alarm) => {
    try {
      const { sound } = await Audio.Sound.createAsync(
        require('./assets/alarm_default.mp3'),
        { shouldPlay: true, isLooping: true }
      );
      setAlarmSound(sound);
      setCurrentRingingAlarm(alarm);
      setIsAlarmRinging(true);
    } catch (error) {
      console.log('Error playing alarm sound:', error);
      Alert.alert('Error', 'Could not play alarm sound');
    }
  };

  const dismissAlarm = async () => {
    if (alarmSound) {
      await alarmSound.stopAsync();
      await alarmSound.unloadAsync();
      setAlarmSound(null);
    }
    setIsAlarmRinging(false);
    setCurrentRingingAlarm(null);
  };

  const createAlarm = () => {
    const hour = parseInt(newAlarmHour);
    const minute = parseInt(newAlarmMinute);

    if (hour < 1 || hour > 12 || minute < 0 || minute > 59) {
      Alert.alert('Invalid Time', 'Please enter a valid time');
      return;
    }

    const newAlarm: Alarm = {
      id: Date.now().toString(),
      hour,
      minute,
      ampm: newAlarmAmPm,
      isActive: true,
    };

    setAlarms([...alarms, newAlarm]);
    setShowCreateAlarm(false);
    setNewAlarmHour('12');
    setNewAlarmMinute('00');
    setNewAlarmAmPm('AM');
    
    Alert.alert('Alarm Created', `Alarm set for ${hour}:${minute.toString().padStart(2, '0')} ${newAlarmAmPm}`);
  };

  const deleteAlarm = (id: string) => {
    setAlarms(alarms.filter(alarm => alarm.id !== id));
  };

  const toggleAlarm = (id: string) => {
    setAlarms(alarms.map(alarm => 
      alarm.id === id ? { ...alarm, isActive: !alarm.isActive } : alarm
    ));
  };

  const formatTime = (hour: number, minute: number, ampm: 'AM' | 'PM') => {
    return `${hour}:${minute.toString().padStart(2, '0')} ${ampm}`;
  };

  if (isAlarmRinging && currentRingingAlarm) {
    return (
      <SafeAreaView style={styles.alarmRingingContainer}>
        <View style={styles.alarmRingingContent}>
          <Text style={styles.alarmRingingTitle}>ALARM</Text>
          <Text style={styles.alarmRingingTime}>
            {formatTime(currentRingingAlarm.hour, currentRingingAlarm.minute, currentRingingAlarm.ampm)}
          </Text>
          <Text style={styles.alarmRingingSubtitle}>Time to wake up!</Text>
          
          <TouchableOpacity style={styles.dismissButton} onPress={dismissAlarm}>
            <Text style={styles.dismissButtonText}>DISMISS</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.centeredContent}>
        <Text style={styles.welcomeText}>AutoRise Alarm</Text>
        
        <View style={styles.timeContainer}>
          <Text style={styles.timeText}>
            {currentTime.toLocaleTimeString()}
          </Text>
          <Text style={styles.dateText}>
            {currentTime.toDateString()}
          </Text>
        </View>

        <View style={styles.alarmsContainer}>
          <Text style={styles.alarmsTitle}>Your Alarms</Text>
          {alarms.length === 0 ? (
            <Text style={styles.noAlarmsText}>No alarms set</Text>
          ) : (
            alarms.map(alarm => (
              <View key={alarm.id} style={styles.alarmItem}>
                <View style={styles.alarmTimeInfo}>
                  <Text style={[styles.alarmTimeText, !alarm.isActive && styles.inactiveAlarm]}>
                    {formatTime(alarm.hour, alarm.minute, alarm.ampm)}
                  </Text>
                  <Text style={[styles.alarmStatus, !alarm.isActive && styles.inactiveStatus]}>
                    {alarm.isActive ? 'Active' : 'Inactive'}
                  </Text>
                </View>
                <View style={styles.alarmActions}>
                  <TouchableOpacity 
                    style={[styles.toggleButton, alarm.isActive ? styles.activeToggle : styles.inactiveToggle]}
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
                    <Text style={styles.deleteButtonText}>×</Text>
                  </TouchableOpacity>
                </View>
              </View>
            ))
          )}
        </View>

        <TouchableOpacity style={styles.createButton} onPress={() => setShowCreateAlarm(true)}>
          <Text style={styles.createButtonText}>+ Create New Alarm</Text>
        </TouchableOpacity>
      </View>

      <Modal visible={showCreateAlarm} animationType="slide" transparent>
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <Text style={styles.modalTitle}>Create New Alarm</Text>
            
            <View style={styles.timeInputContainer}>
              <View style={styles.timeInputGroup}>
                <Text style={styles.inputLabel}>Hour</Text>
                <TextInput
                  style={styles.timeInput}
                  value={newAlarmHour}
                  onChangeText={setNewAlarmHour}
                  keyboardType="numeric"
                  maxLength={2}
                  placeholder="12"
                />
              </View>
              
              <Text style={styles.timeSeparator}>:</Text>
              
              <View style={styles.timeInputGroup}>
                <Text style={styles.inputLabel}>Minute</Text>
                <TextInput
                  style={styles.timeInput}
                  value={newAlarmMinute}
                  onChangeText={setNewAlarmMinute}
                  keyboardType="numeric"
                  maxLength={2}
                  placeholder="00"
                />
              </View>
            </View>

            <View style={styles.ampmContainer}>
              <TouchableOpacity 
                style={[styles.ampmButton, newAlarmAmPm === 'AM' && styles.selectedAmPm]}
                onPress={() => setNewAlarmAmPm('AM')}
              >
                <Text style={[styles.ampmText, newAlarmAmPm === 'AM' && styles.selectedAmPmText]}>AM</Text>
              </TouchableOpacity>
              <TouchableOpacity 
                style={[styles.ampmButton, newAlarmAmPm === 'PM' && styles.selectedAmPm]}
                onPress={() => setNewAlarmAmPm('PM')}
              >
                <Text style={[styles.ampmText, newAlarmAmPm === 'PM' && styles.selectedAmPmText]}>PM</Text>
              </TouchableOpacity>
            </View>

            <View style={styles.modalButtons}>
              <TouchableOpacity style={styles.cancelButton} onPress={() => setShowCreateAlarm(false)}>
                <Text style={styles.cancelButtonText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.saveButton} onPress={createAlarm}>
                <Text style={styles.saveButtonText}>Save Alarm</Text>
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
    backgroundColor: 'white',
  },
  centeredContent: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  welcomeText: {
    fontSize: 28,
    color: 'black',
    fontWeight: 'bold',
    marginBottom: 30,
  },
  timeContainer: {
    alignItems: 'center',
    marginBottom: 30,
  },
  timeText: {
    fontSize: 48,
    color: '#333',
    fontWeight: 'bold',
    fontFamily: 'monospace',
  },
  dateText: {
    fontSize: 16,
    color: '#666',
    marginTop: 5,
  },
  alarmsContainer: {
    width: '100%',
    marginBottom: 30,
  },
  alarmsTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 15,
    textAlign: 'center',
  },
  noAlarmsText: {
    fontSize: 16,
    color: '#999',
    textAlign: 'center',
    fontStyle: 'italic',
  },
  alarmItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: '#f5f5f5',
    padding: 15,
    borderRadius: 10,
    marginBottom: 10,
  },
  alarmTimeInfo: {
    flex: 1,
  },
  alarmTimeText: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#333',
  },
  inactiveAlarm: {
    color: '#999',
  },
  alarmStatus: {
    fontSize: 12,
    color: '#4CAF50',
    marginTop: 2,
  },
  inactiveStatus: {
    color: '#999',
  },
  alarmActions: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  toggleButton: {
    paddingHorizontal: 15,
    paddingVertical: 8,
    borderRadius: 15,
    minWidth: 50,
    alignItems: 'center',
  },
  activeToggle: {
    backgroundColor: '#4CAF50',
  },
  inactiveToggle: {
    backgroundColor: '#ddd',
  },
  toggleButtonText: {
    color: 'white',
    fontSize: 12,
    fontWeight: 'bold',
  },
  deleteButton: {
    backgroundColor: '#f44336',
    width: 30,
    height: 30,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
  },
  deleteButtonText: {
    color: 'white',
    fontSize: 18,
    fontWeight: 'bold',
  },
  createButton: {
    backgroundColor: '#2196F3',
    paddingHorizontal: 30,
    paddingVertical: 15,
    borderRadius: 8,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
  },
  createButtonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: 'bold',
  },
  // Alarm Ringing Screen Styles
  alarmRingingContainer: {
    flex: 1,
    backgroundColor: '#ff4444',
    justifyContent: 'center',
    alignItems: 'center',
  },
  alarmRingingContent: {
    alignItems: 'center',
    padding: 40,
  },
  alarmRingingTitle: {
    fontSize: 48,
    fontWeight: 'bold',
    color: 'white',
    marginBottom: 20,
    letterSpacing: 5,
  },
  alarmRingingTime: {
    fontSize: 36,
    fontWeight: 'bold',
    color: 'white',
    marginBottom: 10,
  },
  alarmRingingSubtitle: {
    fontSize: 20,
    color: 'white',
    marginBottom: 50,
  },
  dismissButton: {
    backgroundColor: 'white',
    paddingHorizontal: 40,
    paddingVertical: 20,
    borderRadius: 50,
    elevation: 5,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.3,
    shadowRadius: 5,
  },
  dismissButtonText: {
    color: '#ff4444',
    fontSize: 20,
    fontWeight: 'bold',
  },
  // Modal Styles
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalContent: {
    backgroundColor: 'white',
    borderRadius: 20,
    padding: 30,
    width: '80%',
    maxWidth: 350,
  },
  modalTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#333',
    textAlign: 'center',
    marginBottom: 30,
  },
  timeInputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 30,
  },
  timeInputGroup: {
    alignItems: 'center',
  },
  inputLabel: {
    fontSize: 14,
    color: '#666',
    marginBottom: 5,
  },
  timeInput: {
    borderWidth: 2,
    borderColor: '#ddd',
    borderRadius: 10,
    padding: 15,
    fontSize: 20,
    fontWeight: 'bold',
    textAlign: 'center',
    width: 60,
    backgroundColor: '#f9f9f9',
  },
  timeSeparator: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#333',
    marginHorizontal: 10,
    marginTop: 20,
  },
  ampmContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    marginBottom: 30,
    gap: 20,
  },
  ampmButton: {
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 20,
    borderWidth: 2,
    borderColor: '#ddd',
    backgroundColor: 'white',
  },
  selectedAmPm: {
    backgroundColor: '#2196F3',
    borderColor: '#2196F3',
  },
  ampmText: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#666',
  },
  selectedAmPmText: {
    color: 'white',
  },
  modalButtons: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 15,
  },
  cancelButton: {
    flex: 1,
    backgroundColor: '#ddd',
    paddingVertical: 15,
    borderRadius: 10,
    alignItems: 'center',
  },
  cancelButtonText: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#666',
  },
  saveButton: {
    flex: 1,
    backgroundColor: '#4CAF50',
    paddingVertical: 15,
    borderRadius: 10,
    alignItems: 'center',
  },
  saveButtonText: {
    fontSize: 16,
    fontWeight: 'bold',
    color: 'white',
  },
});
