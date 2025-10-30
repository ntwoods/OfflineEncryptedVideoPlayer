
#!/usr/bin/env sh
# Simple shim. Android Studio will regenerate wrapper if needed.
if command -v ./gradlew >/dev/null 2>&1; then
  exec ./gradlew "$@"
else
  if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
  else
    echo "Use Android Studio to sync Gradle wrapper."
    exit 0
  fi
fi
