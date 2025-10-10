#!/bin/bash

# Script to run the PGP decryption tool
# Assumes compile.sh has been run successfully

BC_VERSION="1.78"
CLASSPATH="target/classes:lib/bcpg-jdk18on-${BC_VERSION}.jar:lib/bcprov-jdk18on-${BC_VERSION}.jar:lib/bcpkix-jdk18on-${BC_VERSION}.jar"

echo "=== Running PGP Decryption Tool ==="
echo

if [ ! -d "target/classes" ]; then
    echo "Error: Classes not found. Please run ./compile.sh first"
    exit 1
fi

# Pass all command line arguments to the Java program
java -cp "$CLASSPATH" com.example.pgp.PgpDecryptionExample "$@"