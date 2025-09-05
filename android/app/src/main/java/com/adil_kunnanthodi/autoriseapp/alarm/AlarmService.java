package com.adil_kunnanthodi.autoriseapp.alarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.adil_kunnanthodi.autoriseapp.MainActivity;
import com.adil_kunnanthodi.autoriseapp.R;

/**
 * Production-grade foreground service for alarm audio playback
 * Features:
 * - Plays alarm with USAGE_ALARM (bypasses DnD)
 * - Audio focus management
 * - Wake lock handling
 * - Vibration patterns
 * - Notification channel support
 */
public class AlarmService extends Service {
    private static final String TAG = "AlarmService";
    private static final String CHANNEL_ID = "alarm_notifications";
    private static final String CHANNEL_NAME = "Alarm Notifications";
    private static final int NOTIFICATION_ID = 1001;
    
    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private PowerManager.WakeLock wakeLock;
    private Vibrator vibrator;
    private boolean isPlaying = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "AlarmService created");
        
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        
        createNotificationChannel();
        acquireWakeLock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "AlarmService started");
        
        String alarmId = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID);
        String alarmLabel = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_LABEL);
        String alarmTime = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_TIME);
        
        // Start foreground immediately (required for Android 8+)
        startForeground(NOTIFICATION_ID, createNotification(alarmLabel, alarmTime));
        
        // Start alarm playback
        startAlarmPlayback(alarmId, alarmLabel);
        
        // Return START_NOT_STICKY so service doesn't restart if killed
        return START_NOT_STICKY;
    }

    private void startAlarmPlayback(String alarmId, String alarmLabel) {
        Log.d(TAG, "Starting alarm playback for: " + alarmId);
        
        try {
            // Request audio focus for alarm (highest priority)
            requestAudioFocus();
            
            // Setup MediaPlayer with alarm audio
            setupMediaPlayer();
            
            // Start vibration pattern
            startVibration();
            
            isPlaying = true;
            Log.i(TAG, "Alarm playback started successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting alarm playback", e);
            // Fallback to system alarm if custom audio fails
            playSystemAlarm();
        }
    }

    private void setupMediaPlayer() throws Exception {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        
        mediaPlayer = new MediaPlayer();
        
        // Set audio attributes for alarm (bypasses Do Not Disturb)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
            mediaPlayer.setAudioAttributes(audioAttributes);
        } else {
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
        }
        
        // Load alarm sound from assets
        try {
            AssetFileDescriptor afd = getAssets().openFd("alarm_default.mp3");
            mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
        } catch (Exception e) {
            Log.w(TAG, "Custom alarm sound not found, using system default", e);
            // Fallback to system alarm sound
            mediaPlayer.setDataSource(this, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI);
        }
        
        mediaPlayer.setLooping(true); // Loop the alarm
        mediaPlayer.setVolume(1.0f, 1.0f); // Maximum volume
        
        mediaPlayer.setOnPreparedListener(mp -> {
            Log.d(TAG, "MediaPlayer prepared, starting playback");
            mp.start();
        });
        
        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
            // Try system alarm as fallback
            playSystemAlarm();
            return true;
        });
        
        mediaPlayer.prepareAsync();
    }

    private void requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Modern audio focus request
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                .setOnAudioFocusChangeListener(focusChange -> {
                    Log.d(TAG, "Audio focus changed: " + focusChange);
                    // Don't pause alarm for focus changes - it's critical
                })
                .build();
            
            int result = audioManager.requestAudioFocus(audioFocusRequest);
            Log.d(TAG, "Audio focus request result: " + result);
        } else {
            // Legacy audio focus request
            int result = audioManager.requestAudioFocus(
                focusChange -> Log.d(TAG, "Audio focus changed: " + focusChange),
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            );
            Log.d(TAG, "Audio focus request result: " + result);
        }
    }

    private void startVibration() {
        if (vibrator != null && vibrator.hasVibrator()) {
            // Create vibration pattern: wait 500ms, vibrate 1000ms, repeat
            long[] pattern = {500, 1000, 500, 1000, 500, 1000, 500, 1000};
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createWaveform(pattern, 0);
                vibrator.vibrate(effect);
            } else {
                vibrator.vibrate(pattern, 0);
            }
            
            Log.d(TAG, "Vibration started");
        }
    }

    private void playSystemAlarm() {
        Log.w(TAG, "Playing system alarm as fallback");
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            
            mediaPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI);
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(true);
                mediaPlayer.start();
                Log.d(TAG, "System alarm started");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to play system alarm", e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for active alarms");
            channel.setSound(null, null); // No sound for notification, alarm handles audio
            channel.enableVibration(false); // No vibration for notification, alarm handles vibration
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String alarmLabel, String alarmTime) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? 
                PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Create stop alarm action
        Intent stopIntent = new Intent(this, AlarmService.class);
        stopIntent.setAction("STOP_ALARM");
        PendingIntent stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? 
                PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoRise Alarm")
            .setContentText((alarmLabel != null ? alarmLabel : "Alarm") + 
                           (alarmTime != null ? " - " + alarmTime : ""))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Cannot be dismissed
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true) // Show as full screen
            .addAction(R.drawable.ic_launcher_foreground, "Stop Alarm", stopPendingIntent)
            .build();
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "AutoRise:AlarmService"
        );
        wakeLock.acquire(10 * 60 * 1000L); // 10 minutes timeout
        Log.d(TAG, "Wake lock acquired");
    }

    public void stopAlarmPlayback() {
        Log.d(TAG, "Stopping alarm playback");
        
        isPlaying = false;
        
        // Stop media player
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
            } catch (Exception e) {
                Log.e(TAG, "Error stopping media player", e);
            }
        }
        
        // Stop vibration
        if (vibrator != null) {
            vibrator.cancel();
        }
        
        // Release audio focus
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            audioManager.abandonAudioFocus(null);
        }
        
        // Release wake lock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        
        // Stop foreground service
        stopForeground(true);
        stopSelf();
        
        Log.d(TAG, "Alarm playback stopped");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "AlarmService destroyed");
        stopAlarmPlayback();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }
}
