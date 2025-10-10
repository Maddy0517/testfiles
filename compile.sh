#!/bin/bash

# Script to compile and run PGP decryption tool without Maven/Gradle
# This script downloads the required Bouncy Castle JAR files and compiles the Java code

echo "=== PGP Decryption Tool - Manual Setup ==="
echo

# Create directories
mkdir -p lib
mkdir -p target/classes

# Bouncy Castle version
BC_VERSION="1.78"

# URLs for Bouncy Castle JARs
BCPG_URL="https://repo1.maven.org/maven2/org/bouncycastle/bcpg-jdk18on/${BC_VERSION}/bcpg-jdk18on-${BC_VERSION}.jar"
BCPROV_URL="https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk18on/${BC_VERSION}/bcprov-jdk18on-${BC_VERSION}.jar"
BCPKIX_URL="https://repo1.maven.org/maven2/org/bouncycastle/bcpkix-jdk18on/${BC_VERSION}/bcpkix-jdk18on-${BC_VERSION}.jar"

echo "Downloading Bouncy Castle libraries..."

# Download JARs if they don't exist
if [ ! -f "lib/bcpg-jdk18on-${BC_VERSION}.jar" ]; then
    echo "Downloading bcpg-jdk18on-${BC_VERSION}.jar..."
    curl -L -o "lib/bcpg-jdk18on-${BC_VERSION}.jar" "$BCPG_URL"
fi

if [ ! -f "lib/bcprov-jdk18on-${BC_VERSION}.jar" ]; then
    echo "Downloading bcprov-jdk18on-${BC_VERSION}.jar..."
    curl -L -o "lib/bcprov-jdk18on-${BC_VERSION}.jar" "$BCPROV_URL"
fi

if [ ! -f "lib/bcpkix-jdk18on-${BC_VERSION}.jar" ]; then
    echo "Downloading bcpkix-jdk18on-${BC_VERSION}.jar..."
    curl -L -o "lib/bcpkix-jdk18on-${BC_VERSION}.jar" "$BCPKIX_URL"
fi

echo "Compiling Java source code..."

# Compile the Java code
CLASSPATH="lib/bcpg-jdk18on-${BC_VERSION}.jar:lib/bcprov-jdk18on-${BC_VERSION}.jar:lib/bcpkix-jdk18on-${BC_VERSION}.jar"

javac -cp "$CLASSPATH" -d target/classes src/main/java/com/example/pgp/*.java

if [ $? -eq 0 ]; then
    echo "Compilation successful!"
    echo
    echo "To run the program:"
    echo "  ./run.sh"
    echo
    echo "Or manually:"
    echo "  java -cp \"target/classes:$CLASSPATH\" com.example.pgp.PgpDecryptionExample"
else
    echo "Compilation failed!"
    exit 1
fi