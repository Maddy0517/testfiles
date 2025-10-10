# PGP File Decryption Tool

A simple Java application to decrypt PGP encrypted files using a private key and passphrase.

## Features

- Decrypt PGP encrypted files using Bouncy Castle library
- Support for both interactive and command-line usage modes
- Integrity verification of decrypted data
- Simple and clean Java API

## Prerequisites

- Java 11 or higher
- Maven 3.6+
- PGP private key file
- Passphrase for the private key

## Dependencies

This project uses the Bouncy Castle cryptography library:
- `bcpg-jdk18on` - PGP implementation
- `bcprov-jdk18on` - Cryptographic provider
- `bcpkix-jdk18on` - PKI support

## Project Structure

```
pgp-decryption/
├── pom.xml
├── README.md
└── src/main/java/com/example/pgp/
    ├── PgpFileDecryptor.java    # Main decryption logic
    └── PgpDecryptionExample.java # Example usage and main method
```

## Installation and Setup

1. Clone or download this project
2. Navigate to the project directory
3. Compile the project:
   ```bash
   mvn compile
   ```

## Usage

### Method 1: Interactive Mode

Run without arguments for interactive prompts:

```bash
mvn compile exec:java
```

You'll be prompted to enter:
- Path to encrypted file
- Path to private key file
- Passphrase for private key
- Output file path

### Method 2: Command Line Arguments

```bash
mvn compile exec:java -Dexec.args="encrypted_file.pgp private_key.asc your_passphrase decrypted_output.txt"
```

### Method 3: Direct Java Execution

After compilation:

```bash
# Interactive mode
java -cp target/classes:target/dependency/* com.example.pgp.PgpDecryptionExample

# Command line mode
java -cp target/classes:target/dependency/* com.example.pgp.PgpDecryptionExample encrypted_file.pgp private_key.asc your_passphrase decrypted_output.txt
```

## Code Example

Here's how to use the `PgpFileDecryptor` class directly in your code:

```java
import com.example.pgp.PgpFileDecryptor;

public class MyDecryption {
    public static void main(String[] args) throws Exception {
        PgpFileDecryptor decryptor = new PgpFileDecryptor();
        
        // Decrypt a file
        decryptor.decryptFile(
            "path/to/encrypted_file.pgp",    // Encrypted file
            "path/to/private_key.asc",       // Private key file
            "your_passphrase",               // Key passphrase
            "path/to/output.txt"             // Output file
        );
        
        System.out.println("File decrypted successfully!");
    }
}
```

## File Format Requirements

- **Encrypted File**: Should be a PGP encrypted file (typically `.pgp`, `.asc`, or `.gpg` extension)
- **Private Key**: Should be in ASCII armored format (`.asc`) or binary format (`.gpg`)
- **Output**: Can be any file name/path where you want the decrypted content

## Common Issues and Solutions

### Issue: "Secret key for message not found"
- **Cause**: The private key doesn't match the public key used for encryption
- **Solution**: Ensure you're using the correct private key that corresponds to the public key used for encryption

### Issue: "Wrong passphrase"
- **Cause**: Incorrect passphrase for the private key
- **Solution**: Verify the passphrase is correct

### Issue: "File not found"
- **Cause**: Incorrect file paths
- **Solution**: Check that all file paths are correct and files exist

### Issue: "Message failed integrity check"
- **Cause**: File corruption or tampering
- **Solution**: Verify the encrypted file hasn't been corrupted

## Building a Standalone JAR

To create a standalone JAR with all dependencies:

```bash
mvn clean compile assembly:single
```

Then run:
```bash
java -jar target/pgp-decryption-1.0.0-jar-with-dependencies.jar
```

## Security Considerations

1. **Passphrase Security**: Never hardcode passphrases in your source code
2. **File Permissions**: Ensure private key files have appropriate permissions (600 on Unix systems)
3. **Memory Management**: Passphrases are handled as char arrays and should be cleared after use
4. **Key Storage**: Store private keys securely and never commit them to version control

## License

This project is provided as an example. Use at your own discretion and ensure compliance with your organization's security policies.

## Troubleshooting

If you encounter issues:

1. Verify Java version: `java --version`
2. Verify Maven version: `mvn --version`
3. Check file permissions and paths
4. Ensure the encrypted file was created with the corresponding public key
5. Test with a simple text file first

For additional help, check the error messages which are usually descriptive of the issue.