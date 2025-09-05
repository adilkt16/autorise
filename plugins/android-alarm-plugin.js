const { withAndroidManifest, withStringsXml } = require('@expo/config-plugins');

const withAlarmPermissions = (config) => {
  return withAndroidManifest(config, (config) => {
    const androidManifest = config.modResults;
    const application = androidManifest.manifest.application[0];
    const mainActivity = application.activity.find(
      (activity) => activity.$['android:name'] === '.MainActivity'
    );

    // Add required permissions
    if (!androidManifest.manifest['uses-permission']) {
      androidManifest.manifest['uses-permission'] = [];
    }

    const permissions = [
      'android.permission.SCHEDULE_EXACT_ALARM',
      'android.permission.USE_EXACT_ALARM',
      'android.permission.WAKE_LOCK',
      'android.permission.FOREGROUND_SERVICE',
      'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK',
      'android.permission.POST_NOTIFICATIONS',
      'android.permission.VIBRATE',
      'android.permission.RECEIVE_BOOT_COMPLETED',
      'android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS',
      'android.permission.ACCESS_NOTIFICATION_POLICY'
    ];

    permissions.forEach(permission => {
      const exists = androidManifest.manifest['uses-permission'].find(
        p => p.$['android:name'] === permission
      );
      if (!exists) {
        androidManifest.manifest['uses-permission'].push({
          $: { 'android:name': permission }
        });
      }
    });

    // Configure activity to show over lock screen
    if (mainActivity) {
      if (!mainActivity.$['android:showWhenLocked']) {
        mainActivity.$['android:showWhenLocked'] = 'true';
      }
      if (!mainActivity.$['android:turnScreenOn']) {
        mainActivity.$['android:turnScreenOn'] = 'true';
      }
    }

    // Add alarm service
    if (!application.service) {
      application.service = [];
    }

    const alarmService = {
      $: {
        'android:name': '.alarm.AlarmService',
        'android:enabled': 'true',
        'android:exported': 'false',
        'android:foregroundServiceType': 'mediaPlayback'
      }
    };

    const serviceExists = application.service.find(
      service => service.$['android:name'] === '.alarm.AlarmService'
    );
    if (!serviceExists) {
      application.service.push(alarmService);
    }

    // Add alarm receiver
    if (!application.receiver) {
      application.receiver = [];
    }

    const alarmReceiver = {
      $: {
        'android:name': '.alarm.AlarmReceiver',
        'android:enabled': 'true',
        'android:exported': 'false'
      },
      'intent-filter': [
        {
          action: [
            { $: { 'android:name': 'android.intent.action.BOOT_COMPLETED' } },
            { $: { 'android:name': 'android.intent.action.MY_PACKAGE_REPLACED' } },
            { $: { 'android:name': 'com.autoriseapp.ALARM_TRIGGER' } }
          ]
        }
      ]
    };

    const receiverExists = application.receiver.find(
      receiver => receiver.$['android:name'] === '.alarm.AlarmReceiver'
    );
    if (!receiverExists) {
      application.receiver.push(alarmReceiver);
    }

    // Add alarm activity
    const alarmActivity = {
      $: {
        'android:name': '.alarm.AlarmActivity',
        'android:enabled': 'true',
        'android:exported': 'false',
        'android:launchMode': 'singleTop',
        'android:showWhenLocked': 'true',
        'android:turnScreenOn': 'true',
        'android:excludeFromRecents': 'true',
        'android:theme': '@android:style/Theme.Translucent.NoTitleBar'
      }
    };

    const activityExists = application.activity.find(
      activity => activity.$['android:name'] === '.alarm.AlarmActivity'
    );
    if (!activityExists) {
      application.activity.push(alarmActivity);
    }

    return config;
  });
};

const withAlarmStrings = (config) => {
  return withStringsXml(config, (config) => {
    const strings = config.modResults;
    
    // Add notification channel strings
    const channelName = strings.resources.string.find(
      str => str.$.name === 'alarm_notification_channel_name'
    );
    if (!channelName) {
      strings.resources.string.push({
        $: { name: 'alarm_notification_channel_name' },
        _: 'Alarm Notifications'
      });
    }

    const channelDesc = strings.resources.string.find(
      str => str.$.name === 'alarm_notification_channel_description'
    );
    if (!channelDesc) {
      strings.resources.string.push({
        $: { name: 'alarm_notification_channel_description' },
        _: 'Notifications for active alarms'
      });
    }

    return config;
  });
};

module.exports = (config) => {
  config = withAlarmPermissions(config);
  config = withAlarmStrings(config);
  return config;
};
