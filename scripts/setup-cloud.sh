#!/usr/bin/env bash
set -euo pipefail
cd /workspace/Shirokai-Raids
if [ ! -x /workspace/shared/jdk/bin/javac ]; then
    mkdir -p /workspace/shared/jdk /workspace/shared/jdk-download
    archive=OpenJDK21U-jdk_x64_linux_hotspot_21.0.13_9.tar.gz
    base=https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9
    curl -fLsS "$base/$archive" -o "/workspace/shared/jdk-download/$archive"
    curl -fLsS "$base/$archive.sha256.txt" -o "/workspace/shared/jdk-download/$archive.sha256.txt"
    (cd /workspace/shared/jdk-download && sha256sum -c "$archive.sha256.txt")
    tar -xzf "/workspace/shared/jdk-download/$archive" -C /workspace/shared/jdk --strip-components=1
fi
source scripts/cloud-env.sh
javac -version
bash ./gradlew --no-daemon build
python3 scripts/run_showdown_audit.py
version=$(sed -n 's/^mod_version=//p' gradle.properties)
python3 scripts/verify_jar.py --version "$version"
