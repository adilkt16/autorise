#!/bin/bash
# AutoRise Multi-Device Testing Setup Script

echo "📱 AutoRise Multi-Device Testing Setup"
echo "====================================="

# Check if EAS CLI is installed
if ! command -v eas &> /dev/null; then
    echo "📦 Installing EAS CLI..."
    npm install -g eas-cli
else
    echo "✅ EAS CLI already installed"
fi

echo ""
echo "🔧 Available Testing Methods:"
echo ""
echo "1. 🌟 EAS Build (RECOMMENDED) - Works on ANY Android device"
echo "   - Creates installable APK"
echo "   - Cloud build service"
echo "   - Easy distribution"
echo ""
echo "2. 🔨 Local APK Build - For developers with Android Studio"
echo "   - Requires Android Studio setup"
echo "   - Build locally"
echo ""
echo "3. 📲 Development Build - For team development"
echo "   - Requires Expo Dev Client"
echo "   - Limited functionality"
echo ""

read -p "Choose testing method (1/2/3): " choice

case $choice in
    1)
        echo ""
        echo "🌟 Setting up EAS Build..."
        echo ""
        echo "Step 1: Login to Expo (create account if needed)"
        eas login
        
        echo ""
        echo "Step 2: Building development APK..."
        echo "⏳ This will take 5-10 minutes..."
        eas build --platform android --profile development
        
        echo ""
        echo "✅ Build complete! Share the APK download link with your testers."
        echo "📱 Install on test devices by:"
        echo "   1. Enable 'Install from unknown sources' in Android settings"
        echo "   2. Download and install the APK"
        echo "   3. Grant 'Alarms & reminders' permission"
        echo "   4. Test alarm functionality"
        ;;
        
    2)
        echo ""
        echo "🔨 Setting up Local APK Build..."
        echo ""
        
        # Check if Android is set up
        if [ ! -d "android" ]; then
            echo "📱 Generating Android project..."
            npx expo prebuild --clean
        fi
        
        echo "🔨 Building APK locally..."
        cd android
        ./gradlew assembleDebug
        
        echo ""
        echo "✅ APK built successfully!"
        echo "📍 Location: android/app/build/outputs/apk/debug/app-debug.apk"
        echo ""
        echo "📤 To distribute:"
        echo "   1. Copy APK to shared location"
        echo "   2. Share via cloud storage, email, or USB"
        echo "   3. Install on test devices"
        ;;
        
    3)
        echo ""
        echo "📲 Setting up Development Build..."
        echo ""
        echo "⚠️  Note: Native alarm functionality requires custom dev client"
        echo ""
        echo "🔧 Starting development server..."
        npx expo start --dev-client
        ;;
        
    *)
        echo "❌ Invalid choice. Please run the script again."
        exit 1
        ;;
esac

echo ""
echo "📋 Testing Checklist for Each Device:"
echo "====================================="
echo "□ App installs successfully"
echo "□ Native alarm module loads (isReady: true)"
echo "□ Exact alarm permissions granted"
echo "□ Test alarm triggers in 10 seconds"
echo "□ Alarms work when device is locked"
echo "□ Alarms show over lock screen"
echo "□ Audio bypasses Do Not Disturb mode"
echo "□ Dismiss and snooze work correctly"
echo "□ Alarms survive device restart"
echo ""
echo "📖 See TESTING-GUIDE.md for complete testing documentation"
echo ""
echo "🎯 Happy Testing!"
