#!/bin/bash

# Test script to demonstrate PGP decryption
echo "=== PGP Decryption Test Script ==="
echo

# Set Maven path
export PATH=$PATH:/workspace/apache-maven-3.9.4/bin

# Compile the project
echo "Compiling the project..."
/workspace/apache-maven-3.9.4/bin/mvn compile

if [ $? -eq 0 ]; then
    echo "✓ Compilation successful!"
    echo
    
    echo "Usage examples:"
    echo "1. Interactive mode:"
    echo "   java -cp target/classes com.example.pgp.PGPDecryptionExample"
    echo
    echo "2. Command line mode:"
    echo "   java -cp target/classes com.example.pgp.PGPDecryptionExample encrypted.pgp private.key \"passphrase\" output.txt"
    echo
    echo "3. Without passphrase:"
    echo "   java -cp target/classes com.example.pgp.PGPDecryptionExample encrypted.pgp private.key \"\" output.txt"
    echo
    
    echo "Files created:"
    echo "- pom.xml (Maven configuration)"
    echo "- src/main/java/com/example/pgp/PGPDecryptor.java (Main decryption class)"
    echo "- src/main/java/com/example/pgp/PGPDecryptionExample.java (Example usage)"
    echo "- README.md (Documentation)"
    echo
    
    echo "Ready to decrypt PGP files!"
else
    echo "✗ Compilation failed!"
fi