# PGP File Decryption with Java

This project provides a Java solution for decrypting PGP-encrypted files using a private key. It uses the Bouncy Castle library and is compatible with JDK 17.

## Features

- Decrypt PGP-encrypted files using private keys
- Support for both ASCII-armored (.asc) and binary (.pgp, .gpg) formats
- Command-line and interactive modes
- Comprehensive error handling and user-friendly messages
- File integrity verification
- Passphrase-protected private keys support

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- PGP private key file
- PGP-encrypted file to decrypt

## Setup

1. **Clone or download the project**

2. **Install dependencies:**
   ```bash
   mvn clean install
   ```

3. **Compile the project:**
   ```bash
   mvn compile
   ```

## Usage

### Interactive Mode

Run the application without arguments for interactive mode:

```bash
mvn exec:java -Dexec.mainClass="com.example.pgp.PGPDecryptionExample"
```

Or after compilation:

```bash
java -cp target/classes com.example.pgp.PGPDecryptionExample
```

The application will prompt you for:
- Path to encrypted file
- Path to private key file
- Passphrase (if required)
- Output file path (optional)

### Command Line Mode

```bash
java -cp target/classes com.example.pgp.PGPDecryptionExample <encrypted_file> <private_key> [passphrase] [output_file]
```

**Examples:**

```bash
# With passphrase
java -cp target/classes com.example.pgp.PGPDecryptionExample encrypted.pgp private.key "mypassword" decrypted.txt

# Without passphrase (empty string)
java -cp target/classes com.example.pgp.PGPDecryptionExample encrypted.pgp private.key "" decrypted.txt

# Auto-generate output filename
java -cp target/classes com.example.pgp.PGPDecryptionExample encrypted.pgp private.key "mypassword"
```

## File Formats Supported

- **Encrypted files:** `.pgp`, `.gpg`, `.asc`
- **Private key files:** ASCII-armored (`.asc`) or binary formats

## API Usage

You can also use the `PGPDecryptor` class directly in your code:

```java
import com.example.pgp.PGPDecryptor;

// Decrypt a file
PGPDecryptor.decryptFile(
    "path/to/encrypted.pgp",
    "path/to/private.key", 
    "passphrase",
    "path/to/output.txt"
);

// Check if file is ASCII-armored
boolean isArmored = PGPDecryptor.isArmoredFile("encrypted.asc");
```

## Common Issues and Solutions

### "Secret key for message not found"
- Ensure the private key matches the public key used for encryption
- Verify the private key file is not corrupted
- Check that the encrypted file was encrypted for your key

### "Checksum mismatch" or passphrase errors
- Double-check the passphrase for your private key
- Ensure the private key file is in the correct format

### "Message failed integrity check"
- The encrypted file may be corrupted
- Try re-downloading or re-transferring the encrypted file

## Dependencies

- **Bouncy Castle PGP (bcpg-jdk18on):** 1.76
- **Bouncy Castle Provider (bcprov-jdk18on):** 1.76
- **Bouncy Castle PKIX (bcpkix-jdk18on):** 1.76

## Security Notes

- Keep your private keys secure and never share them
- Use strong passphrases for your private keys
- Verify file integrity after decryption
- Consider using secure deletion for temporary files containing sensitive data

## License

This project is provided as-is for educational and practical purposes.