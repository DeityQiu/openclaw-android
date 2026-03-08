#!/bin/bash
set -e
AOSP=~/aosp
OC=~/openclaw-android

# 1. Copy Bridge Service
mkdir -p $AOSP/packages/apps/OpenClawBridge
cp -r $OC/bridge-service/src $AOSP/packages/apps/OpenClawBridge/
cp $OC/bridge-service/Android.bp $AOSP/packages/apps/OpenClawBridge/
cp $OC/bridge-service/AndroidManifest.xml $AOSP/packages/apps/OpenClawBridge/

# 2. Copy Config App
mkdir -p $AOSP/packages/apps/OpenClawSettings/res/values
cp -r $OC/config-app/src $AOSP/packages/apps/OpenClawSettings/
cp $OC/config-app/Android.bp $AOSP/packages/apps/OpenClawSettings/
cp $OC/config-app/AndroidManifest.xml $AOSP/packages/apps/OpenClawSettings/
echo '<?xml version="1.0" encoding="utf-8"?><resources><string name="app_name">OpenClaw</string></resources>' \
  > $AOSP/packages/apps/OpenClawSettings/res/values/strings.xml

# 3. Patch SystemServer.java
SS=$AOSP/frameworks/base/services/java/com/android/server/SystemServer.java
if ! grep -q "OpenClawBridgeService" $SS; then
    sed -i '/^import com.android.server.wm.WindowManagerService;/a import com.openclaw.bridge.OpenClawBridgeService;' $SS
    python3 -c "
with open('$SS') as f: c = f.read()
ins = '\n            // OpenClaw Bridge Service\n            traceBeginAndSlog(\"StartOpenClawBridgeService\");\n            mSystemServiceManager.startService(OpenClawBridgeService.class);\n            traceEnd();\n'
anchor = 'traceBeginAndSlog(\"MakeWindowManagerServiceReady\");'
c = c.replace(anchor, ins + anchor, 1)
with open('$SS', 'w') as f: f.write(c)
print('SystemServer.java patched')
"
fi

# 4. Add to sailfish device.mk
SAILFISH=$AOSP/device/google/sailfish/device.mk
grep -q "OpenClawBridge" $SAILFISH 2>/dev/null || \
  printf '\nPRODUCT_PACKAGES += OpenClawBridge OpenClawSettings\n' >> $SAILFISH

# 5. whisper.cpp (clone if not present)
if [ ! -d $AOSP/external/whisper/.git ]; then
    mkdir -p $AOSP/external/whisper
    git clone --depth=1 https://github.com/ggerganov/whisper.cpp.git $AOSP/external/whisper
fi

echo "Integration complete. Build with: cd ~/aosp && source build/envsetup.sh && lunch aosp_sailfish-userdebug && make -j50"
