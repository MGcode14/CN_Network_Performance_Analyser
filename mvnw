#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper Shell Script
# ----------------------------------------------------------------------------
DIR="$(cd "$(dirname "$0")" && pwd)"
MAVEN_CMD="$DIR/.mvn/maven/apache-maven-3.9.6/bin/mvn"

if [ ! -x "$MAVEN_CMD" ]; then
    echo "Maven binary not found at $MAVEN_CMD. Please run with Windows or install Maven."
fi

exec "$MAVEN_CMD" "$@"
