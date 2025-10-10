package com.example.pgp;

import java.io.File;
import java.util.Scanner;

/**
 * Example usage of the PGP file decryption utility.
 * This class demonstrates how to use the PgpFileDecryptor to decrypt PGP encrypted files.
 */
public class PgpDecryptionExample {

    public static void main(String[] args) {
        PgpFileDecryptor decryptor = new PgpFileDecryptor();
        Scanner scanner = new Scanner(System.in);

        try {
            // Example 1: Interactive mode - get file paths from user input
            if (args.length == 0) {
                System.out.println("=== PGP File Decryption Tool ===");
                System.out.println();

                System.out.print("Enter path to encrypted file: ");
                String encryptedFile = scanner.nextLine().trim();

                System.out.print("Enter path to private key file: ");
                String privateKeyFile = scanner.nextLine().trim();

                System.out.print("Enter passphrase for private key: ");
                String passphrase = scanner.nextLine();

                System.out.print("Enter output file path: ");
                String outputFile = scanner.nextLine().trim();

                // Validate files exist
                if (!new File(encryptedFile).exists()) {
                    System.err.println("Error: Encrypted file not found: " + encryptedFile);
                    return;
                }

                if (!new File(privateKeyFile).exists()) {
                    System.err.println("Error: Private key file not found: " + privateKeyFile);
                    return;
                }

                System.out.println("\nDecrypting file...");
                decryptor.decryptFile(encryptedFile, privateKeyFile, passphrase, outputFile);
                System.out.println("File successfully decrypted to: " + outputFile);
            }
            // Example 2: Command line arguments mode
            else if (args.length == 4) {
                String encryptedFile = args[0];
                String privateKeyFile = args[1];
                String passphrase = args[2];
                String outputFile = args[3];

                System.out.println("Decrypting file: " + encryptedFile);
                decryptor.decryptFile(encryptedFile, privateKeyFile, passphrase, outputFile);
                System.out.println("File successfully decrypted to: " + outputFile);
            }
            // Example 3: Hardcoded example (for testing)
            else if (args.length == 1 && "example".equals(args[0])) {
                // This is a sample with hardcoded values - replace with your actual file paths
                String encryptedFile = "encrypted_file.pgp";      // Your encrypted file
                String privateKeyFile = "private_key.asc";        // Your private key file
                String passphrase = "your_passphrase";            // Your key passphrase
                String outputFile = "decrypted_output.txt";       // Output file

                System.out.println("Running with example values...");
                System.out.println("Note: Update the file paths in the source code to match your files");
                
                decryptor.decryptFile(encryptedFile, privateKeyFile, passphrase, outputFile);
                System.out.println("File successfully decrypted to: " + outputFile);
            }
            else {
                printUsage();
            }

        } catch (Exception e) {
            System.err.println("Error during decryption: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("1. Interactive mode: java -cp target/classes com.example.pgp.PgpDecryptionExample");
        System.out.println("2. Command line mode: java -cp target/classes com.example.pgp.PgpDecryptionExample <encrypted_file> <private_key_file> <passphrase> <output_file>");
        System.out.println("3. Example mode: java -cp target/classes com.example.pgp.PgpDecryptionExample example");
        System.out.println();
        System.out.println("Or using Maven:");
        System.out.println("  mvn compile exec:java");
        System.out.println("  mvn compile exec:java -Dexec.args=\"encrypted_file.pgp private_key.asc passphrase output.txt\"");
    }
}