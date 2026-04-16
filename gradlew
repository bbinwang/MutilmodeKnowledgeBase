#!/bin/sh
# Gradle wrapper stub - delegates to installed gradle or downloads via wrapper

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Set JAVA_HOME
export JAVA_HOME=/home/wangbin/android-dev/jdk-17.0.2
# Set Android SDK
export ANDROID_HOME=/home/wangbin/android-dev/android-sdk
export ANDROID_SDK_ROOT=/home/wangbin/android-dev/android-sdk

CLASSPATH=gradle/wrapper/gradle-wrapper.jar

exec "$JAVA_HOME/bin/java" \
  -Dorg.gradle.appname="$APP_BASE_NAME" \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
