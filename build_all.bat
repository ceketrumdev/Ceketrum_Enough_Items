@echo off
setlocal
echo ============================================
echo  CEI - construction de tous les groupes
echo ============================================
echo.
echo [1/2] G1 a G6  (1.20.1 -^> 1.21.11, Java 21, Loom 1.14)
call gradlew.bat build %*
if errorlevel 1 goto :fail
echo.
echo [2/2] G7        (26.1 -^> 26.3, Java 25, Loom 1.17 - build autonome)
pushd G7
call gradlew.bat build %*
if errorlevel 1 (popd & goto :fail)
popd

echo.
echo Regroupement des jars dans dist\ ...
if exist dist rmdir /S /Q dist
mkdir dist
for %%G in (G1 G2 G3 G4 G5 G6 G7) do (
  for %%L in (fabric neoforge) do (
    if exist "%%G\%%L\build\libs\cei-%%G-%%L-*.jar" (
      copy /Y "%%G\%%L\build\libs\cei-%%G-%%L-*.jar" dist\ >nul
    )
  )
)
del /Q dist\*-sources.jar 2>nul
echo.
echo Termine. Jars disponibles :
dir /B dist
goto :eof

:fail
echo.
echo *** ECHEC DE LA CONSTRUCTION ***
exit /b 1
