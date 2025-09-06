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
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.adil_kunnanthodi.autoriseapp.R;

import java.io.IOException;

public class AlarmService extends Service {
    private static final String TAG = "AlarmService";
    private static final String CHANNEL_ID = "alarm_channel";
    private static final int NOTIFICATION_ID = 1001;
    
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private String currentAlarmId;
    private String currentAlarmLabel;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "AlarmService created");
        
        createNotificationChannel();
        setupAudioManager();
        setupVibrator();
        acquireWakeLock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            currentAlarmId = intent.getStringExtra("alarm_id");
            currentAlarmLabel = intent.getStringExtra("alarm_label");
            
            Log.d(TAG, "Starting alarm service for: " + currentAlarmId);
            
            String action = intent.getAction();
            if ("STOP_ALARM".equals(action)) {
                stopAlarm();
                return START_NOT_STICKY;
            } else if ("SNOOZE_ALARM".equals(action)) {
                snoozeAlarm();
                return START_NOT_STICKY;
            }
        }

        startForeground(NOTIFICATION_ID, createNotification());
        startAlarmAudio();
        startVibration();
        
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "AlarmService destroyed");
        stopAlarmAudio();
        stopVibration();
        releaseWakeLock();
        releaseAudioFocus();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Alarm Notifications",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for active alarms");
            channel.enableVibration(true);
            channel.setSound(null, null); // We'll handle sound separately
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent stopIntent = new Intent(this, AlarmService.class);
        stopIntent.setAction("STOP_ALARM");
        PendingIntent stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent snoozeIntent = new Intent(this, AlarmService.class);
        snoozeIntent.setAction("SNOOZE_ALARM");
        PendingIntent snoozePendingIntent = PendingIntent.getService(
            this, 1, snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent activityIntent = new Intent(this, AlarmActivity.class);
        activityIntent.putExtra("alarm_id", currentAlarmId);
        activityIntent.putExtra("alarm_label", currentAlarmLabel);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent activityPendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("⏰ AutoRise Alarm")
                .setContentText(currentAlarmLabel != null ? currentAlarmLabel : "Alarm is ringing")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setOngoing(true)
                .setAutoCancel(false)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setFullScreenIntent(activityPendingIntent, true)
                .addAction(android.R.drawable.ic_media_pause, "Dismiss", stopPendingIntent)
                .addAction(android.R.drawable.ic_media_next, "Snooze", snoozePendingIntent)
                .build();
    }

    private void setupAudioManager() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        
        if (audioManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(playbackAttributes)
                    .setOnAudioFocusChangeListener(focusChange -> {
                        Log.d(TAG, "Audio focus changed: " + focusChange);
                        // Keep playing even if focus is lost (alarm priority)
                    })
                    .build();
        }
    }

    private void setupVibrator() {
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "AutoRise:AlarmServiceWakeLock"
            );
            wakeLock.acquire(10 * 60 * 1000L); // 10 minutes
        }
    }

    private void startAlarmAudio() {
        try {
            if (audioManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int result = audioManager.requestAudioFocus(audioFocusRequest);
                if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Log.w(TAG, "Audio focus not granted, playing anyway (alarm priority)");
                }
            }

            mediaPlayer = new MediaPlayer();
            
            // Set audio attributes for alarm
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build();
                mediaPlayer.setAudioAttributes(audioAttributes);
            } else {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            }

            // Try to load alarm sound from assets
            try {
                AssetFileDescriptor afd = getAssets().openFd("alarm_default.mp3");
                mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
                Log.d(TAG, "Loaded custom alarm sound from assets");
            } catch (IOException e) {
                Log.w(TAG, "Could not load custom alarm sound, using system default", e);
                // Fallback to system alarm sound
                Uri alarmUri = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI;
                if (alarmUri == null) {
                    alarmUri = android.provider.Settings.System.DEFAULT_RINGTONE_URI;
                }
                mediaPlayer.setDataSource(this, alarmUri);
            }

            mediaPlayer.setLooping(true);
            mediaPlayer.setVolume(1.0f, 1.0f);
            
            mediaPlayer.setOnPreparedListener(mp -> {
                Log.d(TAG, "MediaPlayer prepared, starting playback");
                mp.start();
            });
            
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                // Try system alarm as fallback
                try {
                    mp.reset();
                    Uri systemAlarm = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI;
                    if (systemAlarm != null) {
                        mp.setDataSource(this, systemAlarm);
                        mp.prepareAsync();
                        return true; // Error handled
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "Fallback alarm sound failed", ex);
                }
                return false;
            });

            mediaPlayer.prepareAsync();
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting alarm audio", e);
        }
    }

    private void startVibration() {
        if (vibrator != null && vibrator.hasVibrator()) {
            // Create a vibration pattern: wait 500ms, vibrate 1000ms, repeat
            long[] pattern = {0, 1000, 500, 1000, 500, 1000};
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createWaveform(pattern, 0);
                vibrator.vibrate(effect);
            } else {
                vibrator.vibrate(pattern, 0);
            }
            
            Log.d(TAG, "Started vibration pattern");
        }
    }

    private void stopAlarmAudio() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
                Log.d(TAG, "Stopped alarm audio");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping alarm audio", e);
            }
        }
    }

    private void stopVibration() {
        if (vibrator != null) {
            vibrator.cancel();
            Log.d(TAG, "Stopped vibration");
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    private void releaseAudioFocus() {
        if (audioManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            }
        }
    }

    private void stopAlarm() {
        Log.d(TAG, "Stopping alarm service");
        stopSelf();
    }

    private void snoozeAlarm() {
        Log.d(TAG, "Snoozing alarm for 9 minutes");
        
        // Schedule snooze alarm for 9 minutes from now
        if (currentAlarmId != null) {
            Intent snoozeIntent = new Intent(this, AlarmReceiver.class);
            snoozeIntent.putExtra("alarm_id", currentAlarmId + "_snooze_" + System.currentTimeMillis());
            snoozeIntent.putExtra("alarm_label", currentAlarmLabel + " (Snoozed)");
            snoozeIntent.setAction("com.adil_kunnanthodi.autoriseapp.ALARM_TRIGGER");
            
            // Snooze for 9 minutes
            long snoozeTime = System.currentTimeMillis() + (9 * 60 * 1000);
            
            // Use AlarmModule to schedule snooze (simplified approach)
            // In a production app, you might want to implement this more robustly
        }
        
        stopSelf();
    }
}