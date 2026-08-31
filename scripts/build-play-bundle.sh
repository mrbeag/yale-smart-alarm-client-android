#!/usr/bin/env bash
set -euo pipefail

project_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
android_sdk=${ANDROID_HOME:-/home/cbeesle1/Android/Sdk}
signing_dir=${XDG_CONFIG_HOME:-"$HOME/.config"}/home-alarm-play
keystore_path=$signing_dir/upload-key.p12
key_alias=home-alarm-upload
default_client_file=/home/cbeesle1/.kiro/crew/workspace/.scratch/yale-client.DWYdEp/repo/yalesmartalarmclient/const.py
client_file=${YALE_CLIENT_CONST_FILE:-$default_client_file}

if [[ -n ${YALE_BASIC_AUTH:-} ]]; then
    yale_basic_auth=$YALE_BASIC_AUTH
elif [[ -f $client_file ]]; then
    yale_basic_auth=$(sed -n 's/^YALE_AUTH_TOKEN = "\([^"]*\)".*/\1/p' "$client_file")
else
    echo "Yale OAuth client source not found." >&2
    echo "Set YALE_BASIC_AUTH or YALE_CLIENT_CONST_FILE, then retry." >&2
    exit 1
fi

if [[ -z $yale_basic_auth ]]; then
    echo "Yale OAuth client value is empty; refusing to build a demo-only release." >&2
    exit 1
fi

read -r -s -p "Upload-key password (store this in your password manager): " upload_password
echo

if (( ${#upload_password} < 12 )); then
    echo "Use an upload-key password of at least 12 characters." >&2
    exit 1
fi

mkdir -p -m 700 "$signing_dir"

if [[ -f $keystore_path ]]; then
    keytool -list \
        -keystore "$keystore_path" \
        -storetype PKCS12 \
        -storepass "$upload_password" \
        -alias "$key_alias" >/dev/null
    echo "Using existing upload key: $keystore_path"
else
    read -r -s -p "Repeat upload-key password: " password_confirmation
    echo
    if [[ $upload_password != "$password_confirmation" ]]; then
        echo "Passwords do not match." >&2
        exit 1
    fi
    unset password_confirmation

    keytool -genkeypair \
        -keystore "$keystore_path" \
        -storetype PKCS12 \
        -storepass "$upload_password" \
        -keypass "$upload_password" \
        -alias "$key_alias" \
        -keyalg RSA \
        -keysize 4096 \
        -validity 10000 \
        -dname "CN=Home Alarm Upload Key"
    chmod 600 "$keystore_path"
    echo "Created upload key: $keystore_path"
fi

export ANDROID_HOME=$android_sdk
export YALE_BASIC_AUTH=$yale_basic_auth
export HOME_ALARM_UPLOAD_STORE_FILE=$keystore_path
export HOME_ALARM_UPLOAD_STORE_PASSWORD=$upload_password
export HOME_ALARM_UPLOAD_KEY_ALIAS=$key_alias
export HOME_ALARM_UPLOAD_KEY_PASSWORD=$upload_password

cleanup() {
    unset yale_basic_auth upload_password YALE_BASIC_AUTH
    unset HOME_ALARM_UPLOAD_STORE_FILE HOME_ALARM_UPLOAD_STORE_PASSWORD
    unset HOME_ALARM_UPLOAD_KEY_ALIAS HOME_ALARM_UPLOAD_KEY_PASSWORD
}
trap cleanup EXIT

cd "$project_dir"
./gradlew \
    :app:testDebugUnitTest \
    :app:lintRelease \
    :app:bundleRelease \
    --no-parallel \
    --max-workers=1

bundle_path=$project_dir/app/build/outputs/bundle/release/app-release.aab
test -f "$bundle_path"
jarsigner -verify -strict "$bundle_path" >/dev/null

echo
echo "Signed Play bundle ready:"
echo "$bundle_path"
echo
echo "Back up this upload key securely:"
echo "$keystore_path"
