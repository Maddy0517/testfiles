# RSA File Encryption/Decryption in Java

This project provides a simple and complete implementation of RSA-based file encryption and decryption in Java.

## Files Overview

1. **RSAKeyGenerator.java** - Generates RSA key pairs (public and private keys)
2. **FileEncryptor.java** - Encrypts files using RSA public key with AES hybrid encryption
3. **FileDecryptor.java** - **Decrypts files using RSA private key** (Main focus)
4. **RSAFileDemo.java** - Complete demo showing the entire workflow

## Key Features

- **Hybrid Encryption**: Uses AES for file content encryption and RSA for AES key encryption
- **Large File Support**: Can handle files of any size (RSA alone has size limitations)
- **Simple API**: Easy-to-use methods for encryption and decryption
- **Error Handling**: Comprehensive error handling with informative messages

## Quick Start - File Decryption

### Method 1: Using the Complete Demo
```bash
# Compile all Java files
javac *.java

# Run the complete demo (generates keys, encrypts, and decrypts)
java RSAFileDemo
```

### Method 2: Manual Step-by-Step Process

```bash
# 1. Generate RSA key pair
java RSAKeyGenerator

# 2. Encrypt a file (if you have one to encrypt)
java FileEncryptor your_file.txt public.key encrypted_file.enc

# 3. Decrypt the file with private key (MAIN USE CASE)
java FileDecryptor encrypted_file.enc private.key decrypted_file.txt
```

## Core Decryption Code

The main decryption functionality is in `FileDecryptor.java`:

```java
// Decrypt file using private key
FileDecryptor.decryptFile("encrypted_file.enc", "private.key", "decrypted_output.txt");
```

## How It Works

1. **Key Loading**: Loads the RSA private key from file
2. **Hybrid Decryption**: 
   - Extracts the encrypted AES key from the file header
   - Decrypts the AES key using RSA private key
   - Uses the decrypted AES key to decrypt the file content
3. **File Output**: Saves the decrypted content to the specified output file

## File Format

Encrypted files use this format:
```
[4 bytes: AES key length][Encrypted AES Key][Encrypted File Content]
```

## Requirements

- Java 8 or higher
- No external dependencies (uses built-in Java cryptography)

## Security Notes

- Uses RSA-2048 for key generation
- Uses AES-256 for file content encryption
- Uses PKCS1Padding for RSA operations
- Uses PKCS5Padding for AES operations