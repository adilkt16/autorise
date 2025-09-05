# 📱 Testing AutoRise on Multiple Android Devices

## 🎯 Best Method: EAS Build (Cloud Build)

**Recommended for multi-device testing** - Creates installable APK files that work on any Android device.

### Step 1: Setup EAS Build
```bash
# Install EAS CLI globally
npm install -g eas-cli

# Login to your Expo account (create one if needed)
eas login

# Initialize EAS in your project
eas build:configure
```

### Step 2: Create Development Build Profile
The `eas.json` file should look like this:
```json
{
  "cli": {
    "version": ">= 5.2.0"
  },
  "build": {
    "development": {
      "developmentClient": true,
      "distribution": "internal",
      "android": {
        "gradleCommand": ":app:assembleDebug"
      }
    },
    "preview": {
      "distribution": "internal",
      "android": {
        "buildType": "apk"
      }
    },
    "production": {
      "android": {
        "buildType": "aab"
      }
    }
  }
}
```

### Step 3: Build for Testing
```bash
# Build development APK (includes native alarm modules)
eas build --platform android --profile development

# Alternative: Build preview APK for wider testing
eas build --platform android --profile preview
```

### Step 4: Distribute to Test Devices
- EAS provides a download link for the APK
- Share the link with testers
- Install on any Android device (enable "Install from unknown sources")

**Advantages:**
- ✅ Works on ANY Android device
- ✅ Includes all native alarm functionality
- ✅ Cloud build (no local Android setup needed)
- ✅ Easy distribution via link
- ✅ Professional distribution method

---

## 🔧 Secondary Method 1: Local APK Build

**For developers with Android Studio setup** - Build APK locally.

### Requirements
- Android Studio installed
- Android SDK configured
- Java JDK 17+

### Steps
```bash
# Generate native Android project
npx expo prebuild --clean

# Build debug APK
cd android
./gradlew assembleDebug

# APK location
# android/app/build/outputs/apk/debug/app-debug.apk
```

### Distribution
```bash
# Copy APK to shared location
cp android/app/build/outputs/apk/debug/app-debug.apk ~/AutoRise-debug.apk

# Share via cloud storage, email, or USB
```

**Advantages:**
- ✅ Full control over build process
- ✅ No cloud dependencies
- ✅ Faster iteration for development

**Disadvantages:**
- ❌ Requires Android development setup
- ❌ Manual distribution needed

---

## 📲 Secondary Method 2: Expo Development Build

**For team development** - Uses Expo's development client.

### Setup
```bash
# Install Expo development client on test devices
# Download from Play Store: "Expo Go" or build custom dev client

# Start development server
npx expo start --dev-client
```

### Limitations
- ⚠️ **Native modules may not work** in standard Expo Go
- ⚠️ Requires custom development build for alarm functionality
- ⚠️ Limited to devices on same network

---

## 🌐 Secondary Method 3: Internal App Sharing (Google Play)

**For organized testing** - Use Google Play's internal testing.

### Setup
1. Create Google Play Console account
2. Upload AAB file via EAS Build production profile
3. Add testers by email
4. Distribute via Play Store internal testing

```bash
# Build production AAB
eas build --platform android --profile production
```

**Advantages:**
- ✅ Professional distribution
- ✅ Automatic updates
- ✅ Play Store security

**Disadvantages:**
- ❌ Requires Google Play Console setup
- ❌ More complex initial setup

---

## 🎯 Recommended Testing Strategy

### Phase 1: Local Development
```bash
# Test on your development device
npx expo run:android
```

### Phase 2: Multi-Device Testing (RECOMMENDED)
```bash
# Create EAS development build
eas build --platform android --profile development

# Share APK link with testers
# Install on multiple devices
```

### Phase 3: Pre-Production Testing
```bash
# Create preview build for final testing
eas build --platform android --profile preview
```

### Phase 4: Production Release
```bash
# Create production build for Play Store
eas build --platform android --profile production
```

---

## 📋 Testing Checklist for Each Device

### Basic Functionality
- [ ] App installs successfully
- [ ] Native alarm module loads (`isReady: true`)
- [ ] Exact alarm permissions can be granted
- [ ] Test alarm triggers in 10 seconds

### Production Alarm Features
- [ ] Alarms work when device is locked
- [ ] Alarms show over lock screen
- [ ] Audio bypasses Do Not Disturb mode
- [ ] Dismiss and snooze work correctly
- [ ] Alarms survive device restart

### Device-Specific Testing
- [ ] Different Android versions (8+)
- [ ] Different OEMs (Samsung, Huawei, OnePlus, etc.)
- [ ] Different screen sizes and resolutions
- [ ] Battery optimization behaviors
- [ ] Custom ROM compatibility

### Stress Testing
- [ ] Multiple alarms scheduled
- [ ] Doze mode simulation
- [ ] Background app restrictions
- [ ] Low memory conditions

---

## 🚀 Quick Start: EAS Build Method

Here's the fastest way to get testing on multiple devices:

```bash
# 1. Install EAS CLI
npm install -g eas-cli

# 2. Login to Expo
eas login

# 3. Configure EAS (if not already done)
eas build:configure

# 4. Build development APK
eas build --platform android --profile development

# 5. Get download link and share with testers
# Link will be provided in terminal output
```

**Total time: ~10-15 minutes** (depending on build queue)

---

## 💡 Pro Tips for Multi-Device Testing

### 1. Device Diversity
Test on:
- **Different Android versions**: 8.0, 9.0, 10, 11, 12, 13, 14
- **Different OEMs**: Samsung, Google Pixel, OnePlus, Huawei, Xiaomi
- **Different RAM levels**: 2GB, 4GB, 6GB+ devices

### 2. Battery Optimization Testing
Each OEM has different battery optimization:
- Samsung: Smart Manager
- Huawei: Battery Optimization  
- OnePlus: Battery Optimization
- Xiaomi: AutoStart Management

### 3. Testing Documentation
Create a shared document for testers:
```
Device: [Brand Model Android Version]
Alarm Scheduling: ✅/❌
Lock Screen Alarm: ✅/❌
DnD Bypass: ✅/❌
Restart Survival: ✅/❌
Notes: [Any issues observed]
```

### 4. Remote Debugging
For issues on remote devices:
```bash
# Enable remote debugging in app
# Add logging to help diagnose issues
# Use crash reporting service
```

**Bottom Line: Use EAS Build for the best multi-device testing experience!** 🎯
