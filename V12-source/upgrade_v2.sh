#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(find "$ROOT" -maxdepth 3 -type f \( -name build.gradle -o -name build.gradle.kts \) -path '*/app/*' -print -quit | sed 's#/build.gradle.*##')"
if [[ -z "$APP_DIR" || ! -d "$APP_DIR" ]]; then
  echo "Could not find the Android app module under $ROOT"
  exit 1
fi

GRADLE_FILE="$(find "$APP_DIR" -maxdepth 1 -type f \( -name build.gradle -o -name build.gradle.kts \) -print -quit)"
NAMESPACE="$(grep -hE '^[[:space:]]*namespace[[:space:]]*=?[[:space:]]*"[^"]+"' "$GRADLE_FILE" 2>/dev/null | head -1 | sed -E 's/.*"([^"]+)".*/\1/' || true)"
if [[ -z "$NAMESPACE" ]]; then
  NAMESPACE="$(grep -hE 'applicationId[[:space:]]*=?[[:space:]]*"[^"]+"' "$GRADLE_FILE" 2>/dev/null | head -1 | sed -E 's/.*"([^"]+)".*/\1/' || true)"
fi
if [[ -z "$NAMESPACE" ]]; then
  echo "Could not determine the Android namespace from $GRADLE_FILE"
  echo 'Add: namespace = "com.example.reps100"'
  exit 1
fi

PKG_DIR="${APP_DIR}/src/main/java/${NAMESPACE//.//}"
mkdir -p "$PKG_DIR" "$APP_DIR/src/main/res/drawable"

sed "s/__PACKAGE__/${NAMESPACE}/g" "$ROOT/v2/MainActivity.java" > "$PKG_DIR/MainActivity.java"
sed "s/__PACKAGE__/${NAMESPACE}/g" "$ROOT/v2/ExerciseData.java" > "$PKG_DIR/ExerciseData.java"
cp "$ROOT/v2/assets/"*.jpg "$APP_DIR/src/main/res/drawable/"

cat > "$APP_DIR/src/main/AndroidManifest.xml" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:label="Reps100"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:screenOrientation="portrait"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

echo "Reps100 V2 source installed."
echo "Namespace: $NAMESPACE"
echo "Exercises: 340"
echo "Photo assets: 3 original generated photo references"
echo
echo "Build with: ./gradlew clean assembleRelease --no-daemon"
