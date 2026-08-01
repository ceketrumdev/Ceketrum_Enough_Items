@echo off
setlocal
echo ============================================
echo  CEI - construction de tous les groupes
echo ============================================
echo.

rem Version courante, lue dans gradle.properties.
rem Sans ce filtre, le joker cei-<G>-<loader>-*.jar ramassait aussi les jars
rem des versions precedentes : Gradle ne supprime jamais les anciens
rem artefacts de build\libs, donc 0.1.3 revenait dans dist a chaque build.
for /f "usebackq tokens=2 delims==" %%V in (`findstr /b "mod_version=" gradle.properties`) do set "MODVER=%%V"
if not defined MODVER (
  echo *** Impossible de lire mod_version dans gradle.properties ***
  exit /b 1
)
for /f "usebackq tokens=2 delims==" %%V in (`findstr /b "mod_version=" G7\gradle.properties`) do set "G7VER=%%V"
if not "%G7VER%"=="%MODVER%" echo *** Attention : G7 est en %G7VER%, la racine en %MODVER% ***
echo Version : %MODVER%
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
    if exist "%%G\%%L\build\libs\cei-%%G-%%L-%MODVER%.jar" (
      copy /Y "%%G\%%L\build\libs\cei-%%G-%%L-%MODVER%.jar" dist\ >nul
    )
  )
)

echo Menage des artefacts d'anciennes versions dans build\libs ...
for %%G in (G1 G2 G3 G4 G5 G6 G7) do (
  for %%L in (fabric neoforge) do (
    if exist "%%G\%%L\build\libs" (
      for %%F in ("%%G\%%L\build\libs\*.jar") do (
        echo %%~nxF | findstr /c:"%MODVER%" >nul || del /Q "%%F"
      )
    )
  )
)

echo.
echo Termine. Jars disponibles :
dir /B dist
goto :eof

:fail
echo.
echo *** ECHEC DE LA CONSTRUCTION ***
exit /b 1
