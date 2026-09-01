@echo off
cd /d %~dp0..
call gradlew.bat installDebug
if errorlevel 1 ( echo BUILD FAILED, see log above ^& exit /b 1 )
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" shell monkey -p com.example.notifyguard -c android.intent.category.LAUNCHER 1
exit /b 0
