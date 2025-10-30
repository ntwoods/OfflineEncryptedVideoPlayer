
@ECHO OFF
where gradle >NUL 2>&1
IF %ERRORLEVEL% EQU 0 (
  gradle %*
) ELSE (
  ECHO Use Android Studio to sync Gradle wrapper.
)
