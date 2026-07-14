@REM Maven Wrapper script for Windows
@REM Downloads Maven if not present, then runs it

@echo off
setlocal

set "MAVEN_PROJECTBASEDIR=%~dp0"
set "MAVEN_WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties"
set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists"
set "MAVEN_VERSION=3.9.9"
set "MAVEN_DIST_DIR=%MAVEN_HOME%\apache-maven-%MAVEN_VERSION%"
set "DOWNLOAD_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip"

if exist "%MAVEN_DIST_DIR%\bin\mvn.cmd" goto runMaven

echo Downloading Maven %MAVEN_VERSION%...
if not exist "%MAVEN_HOME%" mkdir "%MAVEN_HOME%"
set "TMPFILE=%TEMP%\maven-%MAVEN_VERSION%-bin.zip"

powershell -Command "Invoke-WebRequest -Uri '%DOWNLOAD_URL%' -OutFile '%TMPFILE%'"
powershell -Command "Expand-Archive -Path '%TMPFILE%' -DestinationPath '%MAVEN_HOME%' -Force"
del "%TMPFILE%"

:runMaven
"%MAVEN_DIST_DIR%\bin\mvn.cmd" %*
