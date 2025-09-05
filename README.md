# AutoRise - Production Android Alarm App

AutoRise is a production-grade alarm application built with Expo and React Native, featuring a complete native Android alarm system that works reliably even when the device is locked, backgrounded, or in Doze mode.

## 🎯 Features

### Production-Grade Alarm Functionality
- ✅ **AlarmManager.setExactAndAllowWhileIdle** - Survives Doze mode and App Standby
- ✅ **Foreground Service** - Reliable audio playback when device is locked  
- ✅ **USAGE_ALARM Audio** - Bypasses Do Not Disturb mode
- ✅ **Wake Locks** - Turns on screen and prevents device from sleeping
- ✅ **Full-Screen Activity** - Shows alarm UI over lock screen
- ✅ **System Restart Recovery** - Alarms persist through device reboots
- ✅ **Permission Management** - Handles exact alarm permissions gracefully

### UI Features
- Modern React Native interface with real-time clock
- Alarm creation with time picker and custom labels
- Alarm management (create, view, cancel)
- System status monitoring and permission checking
- Test functionality for debugging

## 🏗️ Architecture

### Native Android Components

#### 1. AlarmReceiver (BroadcastReceiver)
- Handles alarm triggers from AlarmManager
- Manages wake locks for reliable operation
- Restores alarms after system restart
- Uses `setExactAndAllowWhileIdle` for Doze mode compatibility

#### 2. AlarmService (Foreground Service)
- Plays alarm audio using MediaPlayer with USAGE_ALARM
- Manages audio focus and DnD bypass
- Creates foreground notification for system visibility
- Handles vibration patterns and wake lock management

#### 3. AlarmActivity (Full-Screen Activity)
- Displays over lock screen with `setShowWhenLocked`
- Provides dismiss and snooze functionality
- Prevents back button dismissal during alarm
- Manages screen turn-on and keyguard dismissal

#### 4. AlarmModule (React Native Bridge)
- Exposes native alarm functionality to React Native
- Manages alarm persistence with SharedPreferences
- Handles permission checking and requests
- Provides TypeScript interface for alarm management

#### 5. Expo Plugin System
- Automatically configures Android manifest permissions
- Declares required services and receivers
- Handles build-time native integration

### React Native Layer

#### ProductionAlarmManager
- TypeScript interface for alarm management
- Input validation and error handling
- Convenience methods for common operations
- Permission status monitoring

#### ProductionApp.tsx
- Complete UI for alarm management
- Real-time status monitoring
- Permission handling and user guidance
- Test functionality for development

## 🚀 Getting Started

### Prerequisites
- Node.js 18+ and npm/yarn
- Android Studio and Android SDK
- Physical Android device (recommended for testing)
- Expo CLI (`npm install -g expo-cli`)

### Installation

1. **Clone and Install Dependencies**
   ```bash
   cd /path/to/autorise
   npm install
   ```

2. **Generate Native Code**
   ```bash
   npx expo prebuild --clean
   ```

3. **Build and Install**
   ```bash
   npx expo run:android
   ```

### Development Build
For testing native alarm functionality:
```bash
# Generate development build
npx expo prebuild --clean

# Run on connected Android device
npx expo run:android

# Or use EAS build for cloud builds
eas build --platform android --profile development
```

## 📱 Usage

### Setting Up Alarms
1. Launch the app on your Android device
2. Grant "Alarms & reminders" permission when prompted
3. Create alarms using the "Create Alarm" button
4. Test functionality with the "Test Alarm" feature

### Testing Production Features
```bash
# Test immediate alarm (10 seconds)
Tap "Test Alarm" in the app

# Test lock screen functionality
1. Create a test alarm
2. Lock your device
3. Verify alarm rings and shows over lock screen

# Test Doze mode compatibility  
1. Set alarm for future time
2. Enable Doze mode simulation in developer options
3. Verify alarm still triggers reliably
```

## 🔧 Configuration

### Android Permissions
The app automatically configures these permissions via Expo plugin:
- `SCHEDULE_EXACT_ALARM` - For exact alarm scheduling
- `WAKE_LOCK` - To turn on screen during alarms  
- `FOREGROUND_SERVICE` - For reliable audio playback
- `POST_NOTIFICATIONS` - For alarm notifications
- `VIBRATE` - For alarm vibration patterns

### Build Configuration

#### app.json
```json
{
  "expo": {
    "plugins": [
      "./plugins/android-alarm-plugin.js"
    ]
  }
}
```

#### Development vs Production
- **Development**: Use `expo run:android` for local testing
- **Production**: Use `eas build` for distribution builds

## 🧪 Testing

### Manual Testing Checklist
- [ ] Alarms trigger when app is backgrounded
- [ ] Alarms trigger when device is locked
- [ ] Alarms show over lock screen
- [ ] Audio plays using alarm volume (not media volume)
- [ ] Alarms bypass Do Not Disturb mode
- [ ] Alarms survive device restart
- [ ] Permission handling works correctly
- [ ] Dismiss and snooze functionality works

### Automated Testing
```bash
# Run tests (when implemented)
npm test

# Test native module integration
# Use "Test Alarm" feature in app
```

## 📁 Project Structure

```
autorise/
├── src/
│   └── services/
│       └── ProductionAlarmManager.ts    # TypeScript alarm interface
├── android/
│   └── app/src/main/java/.../alarm/
│       ├── AlarmReceiver.java           # Alarm trigger handling
│       ├── AlarmService.java            # Audio playback service  
│       ├── AlarmActivity.java           # Lock screen UI
│       ├── AlarmModule.java             # React Native bridge
│       └── AlarmPackage.java            # Module registration
├── plugins/
│   └── android-alarm-plugin.js          # Expo build plugin
├── assets/
│   └── alarm_default.mp3                # Alarm audio file
├── ProductionApp.tsx                     # Main application UI
├── app.json                              # Expo configuration
├── package.json                          # Dependencies and scripts
└── README.md                             # This file
```

## 🔐 Security Considerations

### Battery Optimization
- App requests to be whitelisted from battery optimization
- Uses `setExactAndAllowWhileIdle` for Doze mode compatibility
- Foreground service prevents system from killing alarm playback

### Permission Model
- Follows Android 12+ exact alarm permission requirements
- Gracefully handles permission denial scenarios
- Provides clear user guidance for permission setup

### Data Privacy
- Alarm data stored locally using SharedPreferences
- No network communication for core alarm functionality
- User data remains on device

## 🐛 Troubleshooting

### Common Issues

#### "Native module not available"
```bash
# Rebuild native code
npx expo prebuild --clean
npx expo run:android
```

#### Alarms not triggering
1. Check exact alarm permissions in Android settings
2. Disable battery optimization for the app
3. Verify Do Not Disturb exemptions

#### Build errors
```bash
# Clean and rebuild
npx expo prebuild --clean
cd android && ./gradlew clean
cd .. && npx expo run:android
```

### Debug Steps
1. Check system status in app UI
2. Use test alarm to verify functionality
3. Check Android logcat for native errors:
   ```bash
   adb logcat | grep -E "(AlarmReceiver|AlarmService|AlarmActivity)"
   ```

## 📝 Android Best Practices Implemented

### Alarm Management
- Uses `AlarmManager.setExactAndAllowWhileIdle()` for Doze mode compatibility
- Implements `BOOT_COMPLETED` receiver for alarm restoration
- Handles clock changes and timezone updates

### Audio Playback  
- Uses `USAGE_ALARM` audio attributes for DnD bypass
- Implements proper audio focus management
- Falls back to system alarm if MediaPlayer fails

### Service Management
- Foreground service with proper notification channel
- Handles service lifecycle correctly
- Implements proper wake lock management

### Permission Handling
- Checks `canScheduleExactAlarms()` before scheduling
- Provides user guidance for permission setup
- Graceful degradation when permissions unavailable

## 🚢 Production Deployment

### Release Build
```bash
# Generate release build
eas build --platform android --profile production

# Or build locally
npx expo run:android --variant release
```

### Play Store Preparation
1. Configure signing in `eas.json`
2. Set up app bundle configuration  
3. Test thoroughly on multiple devices
4. Ensure all permissions are properly declared

## 🤝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🙏 Acknowledgments

- Android AlarmManager documentation
- Expo custom development build system
- React Native bridge architecture
- Android audio focus and wake lock best practices
