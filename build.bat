@echo off
set JAVA_HOME=D:\ai\jdk-21.0.2
set GRADLE_USER_HOME=D:\ai\.gradle
set GRADLE_HOME=D:\ai\gradle-extract\gradle-8.10
set PATH=%GRADLE_HOME%\bin;%PATH%

cd /d D:\ai\carpet-ai-fake-player
gradle build --no-daemon
