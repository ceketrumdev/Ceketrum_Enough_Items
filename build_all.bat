@echo off
echo Building all CEI Version Groups (G1 to G5)...
call gradlew.bat build --no-daemon
echo Build process finished!
pause
