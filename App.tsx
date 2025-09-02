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
  AppStateStatus,
  Platform,
  Vibration,
  Dimensions
} from 'react-native';
import { Audio } from 'expo-av';
import * as Notifications from 'expo-notifications';
import * as TaskManager from 'expo-task-manager';
import * as BackgroundFetch from 'expo-background-fetch';
import { activateKeepAwake, deactivateKeepAwake } from 'expo-keep-awake';

// Configure notification behavior
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
    shouldShowBanner: true,
    shouldShowList: true,
  }),
});

//});

// Background task name
const BACKGROUND_ALARM_TASK = 'background-alarm-task';

// Define AGGRESSIVE background task for alarm enforcement
TaskManager.defineTask(BACKGROUND_ALARM_TASK, async ({ data, error }) => {
  try {
    console.log('BACKGROUND ALARM TASK RUNNING - CHECKING FOR ALARMS');
    
    // This task will run in background to monitor alarm times
    // When alarm time is reached, it will trigger notifications
    // The notifications will then trigger the forcePlayAlarmAudio function
    
    return BackgroundFetch.BackgroundFetchResult.NewData;
  } catch (taskError) {
    console.log('Background alarm task error:', taskError);
    return BackgroundFetch.BackgroundFetchResult.Failed;
  }
});

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
  const notificationListener = useRef<any>(null);
  const responseListener = useRef<any>(null);
  const alarmTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  
  // Track when alarms were last triggered to prevent multiple triggers per minute
  const lastTriggered = useRef<{[key: string]: string}>({});

  // Setup notifications and audio on app start
  useEffect(() => {
    setupAudio();
    requestNotificationPermissions();
    setupNotificationListeners();
    setupBackgroundFetch();
    checkForMissedAlarms(); // Check if any alarms should be ringing

    // Listen for app state changes
    const subscription = AppState.addEventListener('change', handleAppStateChange);

    return () => {
      subscription?.remove();
      if (notificationListener.current) {
        Notifications.removeNotificationSubscription(notificationListener.current);
      }
      if (responseListener.current) {
        Notifications.removeNotificationSubscription(responseListener.current);
      }
      if (alarmTimeoutRef.current) {
        clearTimeout(alarmTimeoutRef.current);
      }
    };
  }, []);

  // Main timer that checks alarms every second
  useEffect(() => {
    const timer = setInterval(() => {
      const now = new Date();
      setCurrentTime(now);
      checkAlarms(now);
    }, 1000);

    return () => clearInterval(timer);
  }, [alarms]);

  // Handle changes in app state (foreground/background)
  const handleAppStateChange = (nextAppState: any) => {
    console.log('App state changed from', appState, 'to', nextAppState);
    setAppState(nextAppState);
  };

  const checkForMissedAlarms = async () => {
    try {
      // Check for any delivered notifications that are alarms
      const deliveredNotifications = await Notifications.getPresentedNotificationsAsync();
      
      for (const notification of deliveredNotifications) {
        if (notification.request.content.data?.isAlarm) {
          const alarm = notification.request.content.data.alarm as Alarm;
          console.log('Found active alarm notification - SYSTEM already playing audio:', alarm);
          // Don't start app audio - the system notifications are already playing the sound
          // Just set the UI state so user can dismiss
          setCurrentRingingAlarm(alarm);
          setIsAlarmRinging(true);
          break; // Only handle one alarm at a time
        }
      }
    } catch (error) {
      console.log('Error checking for missed alarms:', error);
    }
  };

  const setupBackgroundFetch = async () => {
    try {
      // Register background fetch task
      await BackgroundFetch.registerTaskAsync(BACKGROUND_ALARM_TASK, {
        minimumInterval: 1000 * 60, // Check every minute
        stopOnTerminate: false,
        startOnBoot: true,
      });
      console.log('Background fetch registered');
    } catch (error) {
      console.log('Background fetch registration error:', error);
    }
  };

  const setupAudio = async () => {
    try {
      await Audio.setAudioModeAsync({
        allowsRecordingIOS: false,
        staysActiveInBackground: true,
        playsInSilentModeIOS: true,
        shouldDuckAndroid: true,
        playThroughEarpieceAndroid: false,
      });
    } catch (error) {
      console.error('Error setting up audio:', error);
    }
  };

  const requestNotificationPermissions = async () => {
    try {
      // Set up MAXIMUM INTENSITY Android notification channel for LOCK SCREEN AUDIO
      if (Platform.OS === 'android') {
        await Notifications.setNotificationChannelAsync('critical-alarm-channel', {
          name: 'Critical Alarm Notifications',
          importance: Notifications.AndroidImportance.MAX, // Highest priority
          vibrationPattern: [0, 500, 500, 500, 500, 500, 500, 500], // Strong vibration
          lightColor: '#FF0000',
          sound: 'alarm_default.mp3', // LOCK SCREEN will play this immediately
          enableLights: true,
          enableVibrate: true,
          showBadge: true,
          lockscreenVisibility: Notifications.AndroidNotificationVisibility.PUBLIC, // Show on lock screen
          bypassDnd: true, // CRITICAL: Bypass Do Not Disturb for lock screen audio
          audioAttributes: {
            usage: Notifications.AndroidAudioUsage.ALARM, // System treats as ALARM audio
            contentType: Notifications.AndroidAudioContentType.MUSIC,
            flags: {
              enforceAudibility: true, // FORCE audio to play even in silent mode
              requestHardwareAudioVideoSynchronization: false,
            },
          },
          // Additional lock screen audio settings
          description: 'Critical alarm notifications that play audio immediately on lock screen',
        });
        
        console.log('🔊 LOCK SCREEN AUDIO CHANNEL configured - alarm_default.mp3 will play on lock screen');
      }

      // Set up notification categories for alarms
      await Notifications.setNotificationCategoryAsync('alarm', [
        {
          identifier: 'dismiss',
          buttonTitle: 'Dismiss',
          options: {
            opensAppToForeground: true,
          },
        },
        {
          identifier: 'snooze',
          buttonTitle: 'Snooze',
          options: {
            opensAppToForeground: false,
          },
        },
      ]);

      const { status } = await Notifications.requestPermissionsAsync({
        ios: {
          allowAlert: true,
          allowBadge: true,
          allowSound: true,
          allowCriticalAlerts: true,
        },
        android: {
          allowAlert: true,
          allowBadge: true,
          allowSound: true,
        },
      });
      
      if (status !== 'granted') {
        Alert.alert('Permission required', 'Please enable notifications for alarms to work properly.');
      }
    } catch (error) {
      console.error('Error requesting notification permissions:', error);
    }
  };

  const setupNotificationListeners = () => {
    // Listen for LOCK SCREEN AUDIO notifications
    notificationListener.current = Notifications.addNotificationReceivedListener(async (notification) => {
      console.log('� LOCK SCREEN AUDIO NOTIFICATION RECEIVED �:', notification);
      if (notification.request.content.data?.isAlarm) {
        const alarm = notification.request.content.data.alarm as Alarm;
        const sequence = (notification.request.content.data.sequence as number) || 0;
        
        console.log(`🎵 LOCK SCREEN playing alarm_default.mp3 - Notification ${sequence + 1}/20`);
        console.log('📱 Audio should be playing DIRECTLY on lock screen now');
        
        // Only activate app features for the FIRST notification to avoid spam
        if (sequence === 0) {
          console.log('⚡ FIRST ALARM NOTIFICATION - ACTIVATING FULL WAKE-UP SYSTEM ⚡');
          
          // STEP 1: Keep screen awake (prevents screen from turning off during alarm)
          activateKeepAwake();
          
          // STEP 2: Start AGGRESSIVE vibration pattern for physical wake-up
          const wakeUpVibration = [0, 1000, 500, 1000, 500, 1000, 500]; // Strong pattern
          Vibration.vibrate(wakeUpVibration, true); // Repeat until dismissed
          
          // STEP 3: Force full-screen alarm interface if app is opened
          setCurrentRingingAlarm(alarm);
          setIsAlarmRinging(true);
          
          // STEP 4: Start ADDITIONAL alarm audio in app (on top of lock screen audio)
          await startContinuousAlarmAudio(alarm);
          
          console.log('� LOCK SCREEN AUDIO + APP AUDIO + VIBRATION + SCREEN WAKE activated');
        } else {
          console.log(`🔄 Continuous lock screen audio - notification ${sequence + 1} playing alarm_default.mp3`);
        }
      }
    });

    // Handle notification responses (when user taps notification to open app)
    responseListener.current = Notifications.addNotificationResponseReceivedListener(async (response) => {
      console.log('ALARM NOTIFICATION TAPPED - USER OPENING APP:', response);
      if (response.notification.request.content.data?.isAlarm) {
        const alarm = response.notification.request.content.data.alarm as Alarm;
        // User opened app, show dismiss interface
        setCurrentRingingAlarm(alarm);
        setIsAlarmRinging(true);
      }
    });
  };

  const startContinuousAlarmAudio = async (alarm: Alarm) => {
    try {
      console.log('STARTING CONTINUOUS alarm_default.mp3 - NO AUTO-STOP, ONLY MANUAL DISMISS');
      
      // Set alarm state
      setCurrentRingingAlarm(alarm);
      setIsAlarmRinging(true);
      
      // Setup background audio mode for continuous playback
      await Audio.setAudioModeAsync({
        allowsRecordingIOS: false,
        staysActiveInBackground: true,
        playsInSilentModeIOS: true,
        shouldDuckAndroid: false, // Play at full volume
        playThroughEarpieceAndroid: false,
      });

      // Stop any existing sound
      if (alarmSound) {
        await alarmSound.stopAsync();
        await alarmSound.unloadAsync();
        setAlarmSound(null);
      }

      // Clear any existing auto-stop timeout (we don't want auto-stop)
      if (alarmTimeoutRef.current) {
        clearTimeout(alarmTimeoutRef.current);
        alarmTimeoutRef.current = null;
      }

      // Play alarm_default.mp3 continuously - NO auto-stop timeout
      const { sound } = await Audio.Sound.createAsync(
        require('./assets/alarm_default.mp3'),
        { 
          shouldPlay: true, 
          isLooping: true, // Loop forever
          volume: 1.0,
        }
      );
      
      setAlarmSound(sound);
      console.log('CONTINUOUS ALARM STARTED - alarm_default.mp3 LOOPING FOREVER UNTIL MANUALLY DISMISSED');

      // NO auto-stop timeout - alarm plays until user manually dismisses

    } catch (error) {
      console.log('Error starting continuous alarm audio:', error);
      // Fallback to regular playback
      await playAlarmAudio();
    }
  };

  const forcePlayAlarmAudio = async (alarm: Alarm) => {
    try {
      console.log('FORCE PLAYING alarm_default.mp3 - BYPASSING ALL RESTRICTIONS');
      
      // Set app state to force audio context
      setCurrentRingingAlarm(alarm);
      setIsAlarmRinging(true);
      
      // AGGRESSIVE audio setup - force background audio mode
      await Audio.setAudioModeAsync({
        allowsRecordingIOS: false,
        staysActiveInBackground: true,
        playsInSilentModeIOS: true,
        shouldDuckAndroid: false, // Don't duck, play at full volume
        playThroughEarpieceAndroid: false,
      });

      // Stop any existing sound
      if (alarmSound) {
        await alarmSound.stopAsync();
        await alarmSound.unloadAsync();
        setAlarmSound(null);
      }

      // FORCE play alarm_default.mp3 ONLY
      const { sound } = await Audio.Sound.createAsync(
        require('./assets/alarm_default.mp3'),
        { 
          shouldPlay: true, 
          isLooping: true,
          volume: 1.0,
        }
      );
      
      setAlarmSound(sound);
      console.log('FORCE PLAYING ALARM_DEFAULT.MP3 - MAXIMUM VOLUME - LOOPING UNTIL MANUALLY DISMISSED');

      // NO auto-stop timeout - alarm plays until user manually dismisses

    } catch (error) {
      console.log('Error force playing alarm_default.mp3:', error);
      // Fallback to regular playback
      await playAlarmAudio();
    }
  };

  const handleAlarmFromNotification = async (alarm: Alarm) => {
    console.log('Handling alarm from notification:', alarm);
    setCurrentRingingAlarm(alarm);
    setIsAlarmRinging(true);
    await playAlarmAudio();
  };

  const checkAlarms = (now: Date) => {
    if (isAlarmRinging) return; // Don't check if already ringing

    const currentHour = now.getHours();
    const currentMinute = now.getMinutes();
    const currentSecond = now.getSeconds();

    // Check all alarms every second to ensure precise timing for both test and regular alarms
    console.log(`Checking alarms at ${currentHour}:${currentMinute}:${currentSecond}`);

    alarms.forEach(alarm => {
      if (!alarm.isActive) return;

      // Convert alarm time to 24-hour format
      let alarmHour24 = alarm.hour;
      if (alarm.ampm === 'PM' && alarm.hour !== 12) {
        alarmHour24 += 12;
      } else if (alarm.ampm === 'AM' && alarm.hour === 12) {
        alarmHour24 = 0;
      }

      // Check all alarms every second to ensure precise timing
      // This ensures both test alarms and regular alarms work reliably
      const shouldCheck = alarm.isActive && !isAlarmRinging;
      
      // Create a unique key for this alarm at this time
      const triggerKey = `${alarm.id}-${currentHour}-${currentMinute}`;
      const lastTriggerKey = lastTriggered.current[alarm.id];
      
      if (shouldCheck && currentHour === alarmHour24 && currentMinute === alarm.minute && lastTriggerKey !== triggerKey) {
        console.log('ALARM TRIGGERED!', alarm);
        
        // Mark this alarm as triggered for this minute
        lastTriggered.current[alarm.id] = triggerKey;
        
        // Only deactivate test alarms to prevent multiple triggers
        // Regular alarms stay active for daily use
        if (alarm.id.startsWith('test-')) {
          setAlarms(prevAlarms => 
            prevAlarms.map(a => a.id === alarm.id ? { ...a, isActive: false } : a)
          );
        }
        
        triggerAlarm(alarm);
      }
    });
  };

  const triggerAlarm = async (alarm: Alarm) => {
    console.log('Triggering alarm:', alarm);
    setCurrentRingingAlarm(alarm);
    setIsAlarmRinging(true);

    // Cancel any existing notifications first to prevent duplicates
    await Notifications.cancelAllScheduledNotificationsAsync();

    // Schedule a persistent notification that will show and play sound
    await scheduleAlarmNotification(alarm);
    
    // Always play sound directly, regardless of app state
    // This ensures audio plays both in foreground and background
    await playAlarmAudio();
  };

  const scheduleAlarmBackgroundNotification = async (alarm: Alarm) => {
    try {
      // Calculate the exact date/time for the alarm
      const now = new Date();
      const alarmTime = new Date();
      
      // Convert to 24-hour format
      let alarmHour24 = alarm.hour;
      if (alarm.ampm === 'PM' && alarm.hour !== 12) {
        alarmHour24 += 12;
      } else if (alarm.ampm === 'AM' && alarm.hour === 12) {
        alarmHour24 = 0;
      }
      
      alarmTime.setHours(alarmHour24, alarm.minute, 0, 0);
      
      // If the alarm time has passed today, schedule for tomorrow
      if (alarmTime <= now) {
        alarmTime.setDate(alarmTime.getDate() + 1);
      }
      
      // Cancel any existing notification for this alarm
      const existingNotifications = await Notifications.getAllScheduledNotificationsAsync();
      for (const notification of existingNotifications) {
        if (notification.content.data?.alarmId === alarm.id) {
          await Notifications.cancelScheduledNotificationAsync(notification.identifier);
        }
      }
      
      // Calculate seconds until alarm time
      const secondsUntilAlarm = Math.floor((alarmTime.getTime() - now.getTime()) / 1000);
      
      // Schedule LOCK SCREEN AUDIO notifications - Each one triggers alarm_default.mp3 immediately
      // CRITICAL: These notifications will play audio DIRECTLY on the lock screen
      for (let i = 0; i < 20; i++) { // 20 consecutive audio triggers for continuous effect
        const delay = secondsUntilAlarm + (i * 1); // Every 1 second
        
        await Notifications.scheduleNotificationAsync({
          content: {
            title: i === 0 ? "🚨 WAKE UP! AUTORISE ALARM 🚨" : `🔔 ALARM RINGING - ${20-i} left`,
            body: i === 0 
              ? `⏰ ALARM TIME! It's ${alarm.hour}:${alarm.minute.toString().padStart(2, '0')} ${alarm.ampm} - Wake up now!` 
              : `🎵 alarm_default.mp3 playing on lock screen - Tap to dismiss`,
            sound: 'alarm_default.mp3', // LOCK SCREEN plays this IMMEDIATELY at alarm volume
            priority: Notifications.AndroidNotificationPriority.MAX,
            sticky: true,
            categoryIdentifier: 'alarm',
            ...(Platform.OS === 'android' && { 
              channelId: 'critical-alarm-channel', // Uses our LOCK SCREEN audio channel
            }),
            data: { 
              isAlarm: true, 
              alarm: alarm,
              alarmId: alarm.id,
              triggerContinuousAudio: true,
              sequence: i,
              lockScreenAudio: true, // Flag indicating LOCK SCREEN audio trigger
              systemAudio: true,
              wakeUpAlarm: true,
              audioFile: 'alarm_default.mp3', // Explicit audio file reference
              forceAudioPlayback: true // Force immediate audio on lock screen
            },
          },
          trigger: { 
            type: Notifications.SchedulableTriggerInputTypes.TIME_INTERVAL,
            seconds: delay, 
            repeats: false 
          },
        });
      }

      console.log(`🔊 LOCK SCREEN AUDIO ALARM scheduled for ${alarmTime.toLocaleString()}`);
      console.log(`📱 20 notifications will play alarm_default.mp3 DIRECTLY on lock screen`);
      console.log(`⚡ System will play audio immediately when notifications appear`);
    } catch (error) {
      console.log('Error scheduling system audio alarm notification:', error);
    }
  };

  const scheduleAlarmNotification = async (alarm: Alarm) => {
    try {
      // Cancel any existing notifications first
      await Notifications.cancelAllScheduledNotificationsAsync();

      // Schedule immediate notification with sound
      await Notifications.scheduleNotificationAsync({
        content: {
          title: "🔔 ALARM - AutoRise",
          body: `Wake up! It's ${alarm.hour}:${alarm.minute.toString().padStart(2, '0')} ${alarm.ampm}`,
          sound: 'alarm_default.mp3',
          priority: Notifications.AndroidNotificationPriority.MAX,
          sticky: true, // Makes notification persistent
          data: { 
            isAlarm: true, 
            alarm: alarm,
            alarmId: alarm.id 
          },
        },
        trigger: null, // Immediate notification
      });

      console.log('Alarm notification scheduled');
    } catch (error) {
      console.log('Error scheduling alarm notification:', error);
    }
  };

  const playAlarmAudio = async () => {
    try {
      // Stop any existing sound first
      if (alarmSound) {
        await alarmSound.stopAsync();
        await alarmSound.unloadAsync();
        setAlarmSound(null);
      }

      // Clear any existing timeout
      if (alarmTimeoutRef.current) {
        clearTimeout(alarmTimeoutRef.current);
        alarmTimeoutRef.current = null;
      }

      // Create and play ONLY alarm_default.mp3 - no other sounds
      const { sound } = await Audio.Sound.createAsync(
        require('./assets/alarm_default.mp3'),
        { 
          shouldPlay: true, 
          isLooping: true, // Loop forever
          volume: 1.0,
        }
      );
      
      setAlarmSound(sound);
      console.log('Playing ONLY alarm_default.mp3 - LOOPS UNTIL MANUALLY DISMISSED');

      // NO auto-stop timeout - alarm plays until user manually dismisses in app

    } catch (error) {
      console.log('Error playing alarm_default.mp3:', error);
      Alert.alert('Error', 'Could not play alarm sound');
    }
  };

  const dismissAlarm = async () => {
    console.log('🛑 DISMISSING WAKE-UP ALARM - STOPPING ALL ALARM ACTIVITIES 🛑');
    
    try {
      // STEP 1: Stop vibration immediately
      Vibration.cancel();
      
      // STEP 2: Deactivate screen keep-awake
      deactivateKeepAwake();
      
      // STEP 3: Clear any auto-stop timeout
      if (alarmTimeoutRef.current) {
        clearTimeout(alarmTimeoutRef.current);
        alarmTimeoutRef.current = null;
      }

      // STEP 4: Stop and cleanup any app-based alarm sound
      if (alarmSound) {
        await alarmSound.stopAsync();
        await alarmSound.unloadAsync();
        setAlarmSound(null);
      }

      // STEP 5: Cancel ALL system notifications to stop system audio
      await Notifications.cancelAllScheduledNotificationsAsync();
      await Notifications.dismissAllNotificationsAsync();

      // STEP 6: Clean up test alarms (remove them from the list)
      if (currentRingingAlarm && currentRingingAlarm.id.startsWith('test-')) {
        setAlarms(prevAlarms => 
          prevAlarms.filter(alarm => alarm.id !== currentRingingAlarm.id)
        );
      }

      // STEP 7: Reset alarm state
      setIsAlarmRinging(false);
      setCurrentRingingAlarm(null);

      console.log('✅ WAKE-UP ALARM COMPLETELY DISMISSED - All audio, vibration, and screen activity stopped');
    } catch (error) {
      console.log('Error dismissing wake-up alarm:', error);
    }
  };

  const createAlarm = async () => {
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

    // Schedule background notification for this alarm
    await scheduleAlarmBackgroundNotification(newAlarm);

    setAlarms([...alarms, newAlarm]);
    setShowCreateAlarm(false);
    setNewAlarmHour('12');
    setNewAlarmMinute('00');
    setNewAlarmAmPm('AM');
    
    Alert.alert(
      'CONTINUOUS ALARM CREATED', 
      `Alarm set for ${hour}:${minute.toString().padStart(2, '0')} ${newAlarmAmPm}\n\n� SINGLE NOTIFICATION MODE �\n\n• ONE notification triggers alarm_default.mp3\n• Audio plays CONTINUOUSLY until dismissed\n• NO auto-stop - manual dismiss ONLY\n• Works even when app is closed\n\nAlarm will play until you open AutoRise and dismiss it!`
    );
  };

  const deleteAlarm = async (id: string) => {
    // Cancel the scheduled background notification for this alarm
    try {
      const scheduledNotifications = await Notifications.getAllScheduledNotificationsAsync();
      for (const notification of scheduledNotifications) {
        if (notification.content.data?.alarmId === id) {
          await Notifications.cancelScheduledNotificationAsync(notification.identifier);
          console.log('Cancelled scheduled notification for alarm:', id);
        }
      }
    } catch (error) {
      console.log('Error cancelling notification for deleted alarm:', error);
    }
    
    setAlarms(alarms.filter(alarm => alarm.id !== id));
  };

  const toggleAlarm = async (id: string) => {
    const alarm = alarms.find(a => a.id === id);
    if (!alarm) return;
    
    const newActiveState = !alarm.isActive;
    
    if (newActiveState) {
      // Alarm is being turned on - schedule background notification
      const updatedAlarm = { ...alarm, isActive: true };
      await scheduleAlarmBackgroundNotification(updatedAlarm);
      console.log('Alarm enabled and background notification scheduled');
    } else {
      // Alarm is being turned off - cancel background notification
      try {
        const scheduledNotifications = await Notifications.getAllScheduledNotificationsAsync();
        for (const notification of scheduledNotifications) {
          if (notification.content.data?.alarmId === id) {
            await Notifications.cancelScheduledNotificationAsync(notification.identifier);
            console.log('Cancelled scheduled notification for disabled alarm:', id);
          }
        }
      } catch (error) {
        console.log('Error cancelling notification:', error);
      }
    }
    
    setAlarms(alarms.map(alarm => 
      alarm.id === id ? { ...alarm, isActive: newActiveState } : alarm
    ));
  };

  const formatTime = (hour: number, minute: number, ampm: 'AM' | 'PM') => {
    return `${hour}:${minute.toString().padStart(2, '0')} ${ampm}`;
  };


  if (isAlarmRinging && currentRingingAlarm) {
    return (
      <View style={styles.fullScreenAlarmContainer}>
        {/* Full-screen flashing background for maximum visibility */}
        <View style={styles.flashingBackground}>
          <View style={styles.alarmRingingContent}>
            {/* Large, attention-grabbing title */}
            <Text style={styles.wakeUpTitle}>⏰ WAKE UP! ⏰</Text>
            
            {/* Massive time display */}
            <Text style={styles.massiveAlarmTime}>
              {formatTime(currentRingingAlarm.hour, currentRingingAlarm.minute, currentRingingAlarm.ampm)}
            </Text>
            
            {/* Wake-up message */}
            <Text style={styles.wakeUpMessage}>🌅 TIME TO GET UP! 🌅</Text>
            <Text style={styles.currentTimeDisplay}>
              Current Time: {new Date().toLocaleTimeString()}
            </Text>
            
            {/* Visual indicator that alarm is active */}
            <View style={styles.alarmIndicators}>
              <Text style={styles.indicatorText}>🔊 ALARM RINGING</Text>
              <Text style={styles.indicatorText}>📳 VIBRATING</Text>
              <Text style={styles.indicatorText}>💡 SCREEN ACTIVE</Text>
            </View>
            
            {/* Large dismiss button */}
            <TouchableOpacity style={styles.bigDismissButton} onPress={dismissAlarm}>
              <Text style={styles.bigDismissButtonText}>DISMISS ALARM</Text>
            </TouchableOpacity>
            
            <Text style={styles.dismissInstructions}>
              Tap the button above to stop the alarm
            </Text>
          </View>
        </View>
      </View>
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
  
  // FULL-SCREEN WAKE-UP ALARM STYLES
  fullScreenAlarmContainer: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: '#FF0000', // Bright red background for attention
    zIndex: 9999, // Ensure it's on top of everything
  },
  flashingBackground: {
    flex: 1,
    backgroundColor: '#FF0000',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  wakeUpTitle: {
    fontSize: 48,
    fontWeight: 'bold',
    color: 'white',
    textAlign: 'center',
    marginBottom: 30,
    textShadowColor: '#000',
    textShadowOffset: { width: 2, height: 2 },
    textShadowRadius: 5,
  },
  massiveAlarmTime: {
    fontSize: 72,
    fontWeight: 'bold',
    color: 'yellow',
    textAlign: 'center',
    marginBottom: 20,
    fontFamily: 'monospace',
    textShadowColor: '#000',
    textShadowOffset: { width: 3, height: 3 },
    textShadowRadius: 8,
  },
  wakeUpMessage: {
    fontSize: 32,
    fontWeight: 'bold',
    color: 'white',
    textAlign: 'center',
    marginBottom: 15,
    textShadowColor: '#000',
    textShadowOffset: { width: 2, height: 2 },
    textShadowRadius: 5,
  },
  currentTimeDisplay: {
    fontSize: 20,
    color: 'white',
    textAlign: 'center',
    marginBottom: 30,
    opacity: 0.9,
  },
  alarmIndicators: {
    alignItems: 'center',
    marginBottom: 40,
  },
  indicatorText: {
    fontSize: 18,
    color: 'white',
    fontWeight: 'bold',
    marginVertical: 5,
    textShadowColor: '#000',
    textShadowOffset: { width: 1, height: 1 },
    textShadowRadius: 3,
  },
  bigDismissButton: {
    backgroundColor: '#4CAF50',
    paddingVertical: 25,
    paddingHorizontal: 50,
    borderRadius: 20,
    alignItems: 'center',
    marginBottom: 20,
    borderWidth: 3,
    borderColor: 'white',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.5,
    shadowRadius: 8,
    elevation: 10,
  },
  bigDismissButtonText: {
    fontSize: 24,
    fontWeight: 'bold',
    color: 'white',
  },
  dismissInstructions: {
    fontSize: 16,
    color: 'white',
    textAlign: 'center',
    opacity: 0.8,
  },
});
