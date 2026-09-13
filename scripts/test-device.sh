#!/usr/bin/env bash
set +e
bash gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.bonamassaIntegration=true --stacktrace
test_exit=$?
mkdir -p device-screenshots
adb pull /sdcard/Android/data/br.com.bonamassa.app/files/screenshots/. device-screenshots/ || true
exit "$test_exit"
