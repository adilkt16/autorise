#!/bin/bash
# AutoRise Production Build Script

echo "🚀 Building AutoRise Production Alarm App"
echo "========================================"

# Check if we're in the right directory
if [ ! -f "app.json" ]; then
    echo "❌ Error: Please run this script from the AutoRise project root directory"
    exit 1
fi

echo "📋 Step 1: Cleaning previous builds..."
npx expo prebuild --clean

echo "📱 Step 2: Building Android app..."
npx expo run:android

echo "✅ Build Complete!"
echo ""
echo "📱 Your production alarm app is now installed on your device!"
echo ""
echo "🧪 Next Steps:"
echo "1. Grant 'Alarms & reminders' permission when prompted"
echo "2. Test the alarm functionality with 'Test Alarm' button"
echo "3. Create your first production alarm"
echo "4. Lock your device and verify the alarm works over lock screen"
echo ""
echo "🔧 Troubleshooting:"
echo "- If build fails, ensure Android SDK and Java are properly configured"
echo "- If permissions are denied, check Android Settings > Apps > AutoRise > Permissions"
echo "- If alarms don't trigger, disable battery optimization for AutoRise"
echo ""
echo "📖 See README.md for complete documentation"
