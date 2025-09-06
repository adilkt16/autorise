package com.adil_kunnanthodi.autoriseapp.alarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
import android.os.Vibrator;
import android.util.Log;
import androidx.core.app.NotificationCompat;

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

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        
        // Acquire wake lock
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
            "AutoRise:AlarmServiceWakeLock"
        );
        wakeLock.acquire(300000); // 5 minutes timeout
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        currentAlarmId = intent.getStringExtra("alarmId");
        String label = intent.getStringExtra("label");
        
        Log.d(TAG, "Starting alarm service for: " + currentAlarmId);
        
        // Start foreground service
        Notification notification = createNotification(label);
        startForeground(NOTIFICATION_ID, notification);
        
        // Request audio focus and start playing alarm
        requestAudioFocusAndPlay();
        
        // Start vibration pattern
        startVibration();
        
        return START_NOT_STICKY; // Don't restart if killed
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        Log.d(TAG, "Destroying alarm service");
        
        stopAlarmPlayback();
        
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
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
            channel.setSound(null, null); // We handle sound ourselves
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String label) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Alarm Active")
            .setContentText(label != null ? label : "AutoRise Alarm")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .build();
    }

    private void requestAudioFocusAndPlay() {
        try {
            // Request audio focus for alarm
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(audioAttributes)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build();
                
                int result = audioManager.requestAudioFocus(audioFocusRequest);
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    startAlarmPlayback();
                } else {
                    Log.w(TAG, "Audio focus not granted, playing anyway");
                    startAlarmPlayback();
                }
            } else {
                int result = audioManager.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                );
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    startAlarmPlayback();
                } else {
                    startAlarmPlayback(); // Play anyway for alarm
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error requesting audio focus", e);
            startAlarmPlayback(); // Fallback to direct playback
        }
    }

    private void startAlarmPlayback() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
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

            // Load alarm sound from assets
            AssetFileDescriptor afd = getAssets().openFd("alarm_default.mp3");
            mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();

            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
            
            Log.d(TAG, "Alarm audio started successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting alarm playback", e);
            // Fallback to system alarm sound
            fallbackToSystemAlarm();
        }
    }

    private void fallbackToSystemAlarm() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            
            mediaPlayer = new MediaPlayer();
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
                mediaPlayer.setAudioAttributes(audioAttributes);
            } else {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            }
            
            // Use system default alarm sound
            android.net.Uri alarmUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION);
            }
            
            mediaPlayer.setDataSource(this, alarmUri);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
            
            Log.d(TAG, "Fallback system alarm started");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start fallback alarm", e);
        }
    }

    private void startVibration() {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                // Vibration pattern: [delay, vibrate, sleep, vibrate, ...]
                long[] pattern = {0, 1000, 500, 1000, 500};
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting vibration", e);
        }
    }

    private void stopAlarmPlayback() {
        try {
            // Stop audio playback
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
            }

            // Release audio focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else if (audioManager != null) {
                audioManager.abandonAudioFocus(focusChangeListener);
            }

            // Stop vibration
            if (vibrator != null) {
                vibrator.cancel();
            }

            Log.d(TAG, "Alarm playback stopped");
            
        } catch (Exception e) {
            Log.e(TAG, "Error stopping alarm playback", e);
        }
    }

    public void stopAlarm() {
        stopAlarmPlayback();
        stopSelf();
    }

    private AudioManager.OnAudioFocusChangeListener focusChangeListener = 
        new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {
                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_LOSS:
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        // For alarms, we want to keep playing even if focus is lost
                        Log.d(TAG, "Audio focus lost but continuing alarm playback");
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN:
                        Log.d(TAG, "Audio focus gained");
                        break;
                }
            }
        };
}
