#!/data/data/com.termux/files/usr/bin/sh
# ============================================================
# 在 Termux 里构建 HotRank 的 Debug APK（全程离线，不需要电脑）
#
# 用法：
#   cd ~/HotRank          # 先解压到 Termux 能访问的目录
#   sh tools/build-termux.sh
#
# 完成后 APK 在：app/build/outputs/apk/debug/app-debug.apk
# 复制到手机存储：cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/
# ============================================================

set -e

SDK="$HOME/android-sdk"
BT_VER="34.0.0"
API="34"
PLATFORM_ZIP="https://dl.google.com/android/repository/platform-34_r03.zip"

echo "[1/5] 安装依赖（JDK 17 + aapt2 + 解压工具）"
pkg update -y >/dev/null 2>&1 || true
pkg install -y openjdk-17 aapt2 zip unzip

# Termux 的 JAVA_HOME 需要显式指定，否则 gradlew 找不到 JDK
export JAVA_HOME="$PREFIX/opt/openjdk"
export PATH="$JAVA_HOME/bin:$PATH"
java -version

echo "[2/5] 搭 Android SDK 骨架（AGP 只认目录结构，不认 sdkmanager）"
mkdir -p "$SDK/platforms/android-$API" "$SDK/build-tools/$BT_VER" "$SDK/licenses"

if [ ! -f "$SDK/platforms/android-$API/android.jar" ]; then
  echo "      下载 platform-$API，取里面的 android.jar（这个 jar 与 CPU 架构无关）"
  curl -L --progress-bar -o /tmp/p.zip "$PLATFORM_ZIP"
  unzip -o -q /tmp/p.zip -d /tmp/p
  cp /tmp/p/android-$API/android.jar "$SDK/platforms/android-$API/"
  # source.properties 记录 API level，AGP 靠它判断，必须一起拷
  [ -f "/tmp/p/android-$API/source.properties" ] && \
    cp "/tmp/p/android-$API/source.properties" "$SDK/platforms/android-$API/" || true
  rm -rf /tmp/p /tmp/p.zip
fi

echo "[3/5] 用 Termux 的 aarch64 版 aapt2 顶掉 Google 的 x86_64 版"
# Google 只发布 x86_64 的 aapt2，在 ARM 手机上是跑不起来的，必须换掉
cp "$(command -v aapt2)" "$SDK/build-tools/$BT_VER/aapt2"
chmod +x "$SDK/build-tools/$BT_VER/aapt2"
cp "$(command -v aapt2)" "$PREFIX/bin/aapt2" 2>/dev/null || true

# 告诉 AGP 直接用指定的 aapt2，绕开 Maven 上的 x86_64 产物
if ! grep -q aapt2FromMavenOverride gradle.properties 2>/dev/null; then
  echo "android.aapt2FromMavenOverride=$SDK/build-tools/$BT_VER/aapt2" >> gradle.properties
fi
# 抑制 compileSdk 版本提示
echo "android.suppressUnsupportedCompileSdk=$API" >> gradle.properties

cat > "$SDK/build-tools/$BT_VER/source.properties" <<EOF
Pkg.Revision=$BT_VER
Pkg.Desc=Android SDK Build-Tools
EOF

# AGP 会检查许可文件，缺了会直接报错
echo "24333f8a63b6825ea9c5514f83c2829b004d1fee" > "$SDK/licenses/android-sdk-license"

echo "[4/5] 写 local.properties"
echo "sdk.dir=$SDK" > local.properties

echo "[5/5] 开始编译（第一次要下载 Gradle 8.7 和依赖，慢，别中断）"
./gradlew assembleDebug --no-daemon

APK="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
  echo ""
  echo "✅ 编译成功：$(pwd)/$APK"
  echo "   复制到手机存储后点一下就能装："
  echo "   cp $(pwd)/$APK /sdcard/Download/"
else
  echo "❌ 没产出 APK。把上面的报错整段发我，我来改脚本。"
  exit 1
fi
