#!/bin/sh
# Maven Wrapper script for Unix systems
# Downloads Maven if not present, then runs it

set -e

MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-$(cd "$(dirname "$0")" && pwd)}"
MAVEN_WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"
MAVEN_HOME="$HOME/.m2/wrapper/dists"

if [ -f "$MAVEN_WRAPPER_PROPERTIES" ]; then
    distributionUrl=$(grep "distributionUrl" "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2-)
fi

distributionUrl="${distributionUrl:-https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip}"

MAVEN_VERSION=$(echo "$distributionUrl" | sed 's/.*apache-maven-\(.*\)-bin.zip/\1/')
MAVEN_DIST_DIR="$MAVEN_HOME/apache-maven-$MAVEN_VERSION"

if [ ! -d "$MAVEN_DIST_DIR" ]; then
    echo "Downloading Maven $MAVEN_VERSION..."
    mkdir -p "$MAVEN_HOME"
    TMPFILE=$(mktemp)
    curl -fsSL "$distributionUrl" -o "$TMPFILE"
    unzip -q "$TMPFILE" -d "$MAVEN_HOME"
    rm -f "$TMPFILE"
fi

exec "$MAVEN_DIST_DIR/bin/mvn" "$@"
