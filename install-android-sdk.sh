#!/bin/bash

set -e
set -u
set -o pipefail

DIR=$(readlink -f "$(dirname "$0")")
SDK_FILE=android-sdk_r24.3.3-linux.tgz
ANDROID_SDK_DOWNLOAD_URL="http://dl.google.com/android/$SDK_FILE"
SDK_DIR="$DIR/sdk"

echo "Removing old installation"
rm -rf "$SDK_DIR/android-sdk-linux"
mkdir -p "$SDK_DIR"

if [ ! -f "$SDK_DIR/$SDK_FILE" ]; then
    echo "Downloading Android SDK"
    wget -4 -O "$SDK_DIR/$SDK_FILE" "$ANDROID_SDK_DOWNLOAD_URL"
fi

echo "Extracting the SDK"
tar xzf "$SDK_DIR/$SDK_FILE" -C "$SDK_DIR"

echo "Installing SDK tools"
# Auto accept Licenses
(while true; do sleep 2; echo y; done) | \
    "$SDK_DIR/android-sdk-linux/tools/android" update sdk \
        --no-ui --all \
        --filter platform-tool,android-19,android-21,android-22,build-tools-22.0.1,extra-android-support,extra-android-m2repository,extra-google-m2repository
