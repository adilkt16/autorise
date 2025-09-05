package com.adil_kunnanthodi.autoriseapp.alarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Vibrator;
import android.util.Log;
import androidx.core.app.NotificationCompat;

/**
 * AlarmService - Foreground service that plays alarm audio
 * This service runs in the foreground and ensures reliable alarm playback
 */
public class AlarmService extends Service implements MediaPlayer.OnPreparedListener, 
        MediaPlayer.OnErrorListener, AudioManager.OnAudioFocusChangeListener {
    
    private static final String TAG = "AlarmService";
    private static final int NOTIFICATION_ID = 12345;
    private static final String CHANNEL_ID = "alarm_service_channel";
    
    public static final String ACTION_START_ALARM = "START_ALARM";
    public static final String ACTION_STOP_ALARM = "STOP_ALARM";
    
    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private NotificationManager notificationManager;
    
    private String currentAlarmId;
    private String currentAlarmTime;
    private boolean isPlaying = false;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "AlarmService created");
        
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        createNotificationChannel();
        acquireWakeLock();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_START_ALARM.equals(intent.getAction())) {
            currentAlarmId = intent.getStringExtra("alarmId");
            currentAlarmTime = intent.getStringExtra("alarmTime");
            
            Log.d(TAG, "🚨 Starting alarm: " + currentAlarmId + " at " + currentAlarmTime);
            
            // Start foreground immediately to avoid ANR
            startForeground(NOTIFICATION_ID, createAlarmNotification());
            
            // Start alarm playback
            startAlarmPlayback();
            
            // Show full-screen alarm UI
            showAlarmActivity();
            
        } else if (intent != null && ACTION_STOP_ALARM.equals(intent.getAction())) {
            Log.d(TAG, "🔇 Stopping alarm service");
            stopAlarmPlayback();
            stopSelf();
        }
        
        // Return START_NOT_STICKY so service doesn't restart if killed
        return START_NOT_STICKY;
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Alarm Service",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Foreground service for alarm playback");
            channel.enableVibration(false); // We handle vibration separately
            channel.setSound(null, null); // We handle audio separately
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    private Notification createAlarmNotification() {
        Intent stopIntent = new Intent(this, AlarmService.class);
        stopIntent.setAction(ACTION_STOP_ALARM);
        PendingIntent stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Intent alarmIntent = new Intent(this, AlarmActivity.class);
        alarmIntent.putExtra("alarmId", currentAlarmId);
        alarmIntent.putExtra("alarmTime", currentAlarmTime);
        PendingIntent alarmPendingIntent = PendingIntent.getActivity(
            this, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚨 ALARM RINGING")
            .setContentText("Alarm: " + (currentAlarmTime != null ? currentAlarmTime : "Now"))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(alarmPendingIntent, true)
            .addAction(android.R.drawable.ic_media_pause, "DISMISS", stopPendingIntent)
            .setContentIntent(alarmPendingIntent)
            .build();
    }
    
    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "AutoRise:AlarmServiceWakeLock"
        );
        wakeLock.acquire(10 * 60 * 1000); // 10 minutes max
        Log.d(TAG, "✅ Wake lock acquired");
    }
    
    private void startAlarmPlayback() {
        try {
            // Request audio focus for alarm
            requestAudioFocus();
            
            // Start vibration pattern
            startVibration();
            
            // Setup and start MediaPlayer
            setupMediaPlayer();
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error starting alarm playback", e);
        }
    }
    
    private void requestAudioFocus() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(this)
                .build();
            
            int result = audioManager.requestAudioFocus(audioFocusRequest);
            Log.d(TAG, "Audio focus request result: " + result);
        } else {
            int result = audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN
            );
            Log.d(TAG, "Audio focus request result (legacy): " + result);
        }
    }
    
    private void startVibration() {
        if (vibrator != null && vibrator.hasVibrator()) {
            // Vibration pattern: [wait, vibrate, wait, vibrate, ...]
            long[] pattern = {0, 1000, 500, 1000, 500, 1000, 500};
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
            Log.d(TAG, "✅ Vibration started");
        }
    }
    
    private void setupMediaPlayer() {
        try {
            mediaPlayer = new MediaPlayer();
            
            // Set audio attributes for alarm
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
            mediaPlayer.setAudioAttributes(audioAttributes);
            
            // Set data source to alarm sound
            // Note: In a real implementation, you'd copy alarm_default.mp3 to res/raw/
            // For now, we'll use a system alarm sound
            Uri alarmUri = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI;
            if (alarmUri == null) {
                alarmUri = android.provider.Settings.System.DEFAULT_RINGTONE_URI;
            }
            
            mediaPlayer.setDataSource(this, alarmUri);
            mediaPlayer.setLooping(true);
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnErrorListener(this);
            
            // Set volume to maximum
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            int maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            am.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0);
            
            mediaPlayer.prepareAsync();
            Log.d(TAG, "📱 MediaPlayer setup complete, preparing...");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error setting up MediaPlayer", e);
        }
    }
    
    @Override
    public void onPrepared(MediaPlayer mp) {
        Log.d(TAG, "🔊 MediaPlayer prepared, starting playback");
        mp.start();
        isPlaying = true;
    }
    
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(TAG, "❌ MediaPlayer error: " + what + ", " + extra);
        return false;
    }
    
    @Override
    public void onAudioFocusChange(int focusChange) {
        Log.d(TAG, "Audio focus change: " + focusChange);
        // For alarms, we typically don't pause on focus loss
        // Alarms should continue playing even if other apps request focus
    }
    
    private void showAlarmActivity() {
        try {
            Intent alarmIntent = new Intent(this, AlarmActivity.class);
            alarmIntent.putExtra("alarmId", currentAlarmId);
            alarmIntent.putExtra("alarmTime", currentAlarmTime);
            alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                               Intent.FLAG_ACTIVITY_CLEAR_TOP |
                               Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(alarmIntent);
            Log.d(TAG, "📱 Alarm activity started");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error starting alarm activity", e);
        }
    }
    
    private void stopAlarmPlayback() {
        Log.d(TAG, "🔇 Stopping alarm playback");
        
        // Stop media player
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
                isPlaying = false;
                Log.d(TAG, "✅ MediaPlayer stopped and released");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping MediaPlayer", e);
            }
        }
        
        // Stop vibration
        if (vibrator != null) {
            vibrator.cancel();
            Log.d(TAG, "✅ Vibration stopped");
        }
        
        // Release audio focus
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            audioManager.abandonAudioFocus(this);
        }
        Log.d(TAG, "✅ Audio focus released");
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "AlarmService destroyed");
        
        stopAlarmPlayback();
        
        // Release wake lock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "✅ Wake lock released");
        }
        
        super.onDestroy();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }
}
