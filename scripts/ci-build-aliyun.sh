#!/usr/bin/env bash

set -euo pipefail

echo "=== OpenClaw Mobile Aliyun CI Build ==="

# Support both repo root and parent directory workspaces.
if [[ ! -f "./gradlew" && -f "./OpenClawMobile/gradlew" ]]; then
  cd OpenClawMobile
fi

if [[ ! -f "./gradlew" ]]; then
  echo "ERROR: gradlew not found. Run this script in repo root."
  exit 1
fi

echo "=== Workspace ==="
pwd
ls -la

echo "=== Java runtime ==="
if [[ -d "/usr/lib/jvm/java-17-openjdk-amd64" ]]; then
  export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
elif [[ -d "/usr/lib/jvm/temurin-17-jdk-amd64" ]]; then
  export JAVA_HOME="/usr/lib/jvm/temurin-17-jdk-amd64"
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
java -version

echo "=== Android SDK detection ==="
SDK_CANDIDATES=(
  "${ANDROID_SDK_ROOT:-}"
  "${ANDROID_HOME:-}"
  "/opt/android-sdk"
  "/opt/hostedtoolcache/android-sdk"
  "$PWD/.android-sdk"
)

ANDROID_SDK_ROOT_RESOLVED=""
for candidate in "${SDK_CANDIDATES[@]}"; do
  if [[ -n "$candidate" && -d "$candidate" ]]; then
    ANDROID_SDK_ROOT_RESOLVED="$candidate"
    break
  fi
done

if [[ -z "$ANDROID_SDK_ROOT_RESOLVED" ]]; then
  ANDROID_SDK_ROOT_RESOLVED="$PWD/.android-sdk"
  mkdir -p "$ANDROID_SDK_ROOT_RESOLVED"
fi

export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT_RESOLVED"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/tools/bin:$PATH"

echo "ANDROID_HOME=$ANDROID_HOME"
echo "ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"

if ! command -v sdkmanager >/dev/null 2>&1; then
  echo "=== Installing Android command line tools ==="
  TOOLS_ZIP="/tmp/cmdline-tools.zip"
  TOOLS_TMP_DIR="/tmp/android-cmdline-tools"
  curl -fL --retry 3 --retry-delay 2 \
    -o "$TOOLS_ZIP" \
    "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  rm -rf "$TOOLS_TMP_DIR"
  mkdir -p "$TOOLS_TMP_DIR"
  unzip -q "$TOOLS_ZIP" -d "$TOOLS_TMP_DIR"
  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
  rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  mv "$TOOLS_TMP_DIR/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
fi

echo "=== Installing SDK packages ==="
yes | sdkmanager --licenses >/dev/null || true
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

echo "=== Writing local.properties ==="
printf "sdk.dir=%s\n" "$ANDROID_SDK_ROOT" > local.properties
cat local.properties

if [[ -z "${ENV_URL:-}" ]]; then
  echo "ERROR: ENV_URL is required."
  echo "Example:"
  echo "ENV_URL='https://.../environment.zip?...' bash scripts/ci-build-aliyun.sh"
  exit 2
fi

echo "=== Downloading environment.zip ==="
mkdir -p app/src/main/assets/
curl -fL --retry 3 --retry-delay 2 -o app/src/main/assets/environment.zip "$ENV_URL"
ls -lh app/src/main/assets/environment.zip

# Basic guardrail to avoid accidentally downloading HTML error pages.
ENV_SIZE_BYTES=$(wc -c < app/src/main/assets/environment.zip || echo 0)
if [[ "$ENV_SIZE_BYTES" -lt 1048576 ]]; then
  echo "ERROR: environment.zip seems too small ($ENV_SIZE_BYTES bytes)."
  exit 3
fi

echo "=== Running Gradle build ==="
chmod +x gradlew
set +e
./gradlew --no-daemon --console=plain --stacktrace clean assembleDebug > gradle_build.log 2>&1
RC=$?
set -e

if [[ "$RC" -ne 0 ]]; then
  echo "=== Build failed, last 250 log lines ==="
  tail -n 250 gradle_build.log || true
  echo "=== Build failed, extracted key error lines ==="
  awk '/What went wrong|Caused by:|FAILURE:|ERROR:|SDK location not found|requires Java|Execution failed|Could not resolve|A problem occurred configuring/ { print }' gradle_build.log | tail -n 120 || true
  exit "$RC"
fi

echo "=== Build output ==="
ls -la app/build/outputs/apk/debug/
test -f app/build/outputs/apk/debug/app-debug.apk
echo "APK build success: app/build/outputs/apk/debug/app-debug.apk"
