package com.example.pgp;

import java.io.File;
import java.util.Scanner;

/**
 * Example class demonstrating how to use the PGPDecryptor to decrypt PGP-encrypted files.
 * This class provides a command-line interface for decrypting files.
 */
public class PGPDecryptionExample {
    
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        try {
            // Get input parameters from user or command line arguments
            String encryptedFilePath;
            String privateKeyPath;
            String passphrase;
            String outputFilePath;
            
            if (args.length >= 3) {
                // Use command line arguments
                encryptedFilePath = args[0];
                privateKeyPath = args[1];
                passphrase = args.length > 3 ? args[2] : null;
                outputFilePath = args.length > 3 ? args[3] : generateOutputFileName(encryptedFilePath);
            } else {
                // Interactive mode
                System.out.println("=== PGP File Decryption Tool ===");
                System.out.println();
                
                System.out.print("Enter path to encrypted file: ");
                encryptedFilePath = scanner.nextLine().trim();
                
                System.out.print("Enter path to private key file: ");
                privateKeyPath = scanner.nextLine().trim();
                
                System.out.print("Enter passphrase for private key (press Enter if no passphrase): ");
                passphrase = scanner.nextLine().trim();
                if (passphrase.isEmpty()) {
                    passphrase = null;
                }
                
                System.out.print("Enter output file path (press Enter for auto-generated): ");
                outputFilePath = scanner.nextLine().trim();
                if (outputFilePath.isEmpty()) {
                    outputFilePath = generateOutputFileName(encryptedFilePath);
                }
            }
            
            // Validate input files exist
            if (!new File(encryptedFilePath).exists()) {
                System.err.println("Error: Encrypted file does not exist: " + encryptedFilePath);
                return;
            }
            
            if (!new File(privateKeyPath).exists()) {
                System.err.println("Error: Private key file does not exist: " + privateKeyPath);
                return;
            }
            
            // Check if the encrypted file is armored
            boolean isArmored = PGPDecryptor.isArmoredFile(encryptedFilePath);
            System.out.println("Encrypted file format: " + (isArmored ? "ASCII-armored" : "Binary"));
            
            // Perform decryption
            System.out.println("Decrypting file...");
            long startTime = System.currentTimeMillis();
            
            PGPDecryptor.decryptFile(encryptedFilePath, privateKeyPath, passphrase, outputFilePath);
            
            long endTime = System.currentTimeMillis();
            System.out.println("Decryption completed successfully!");
            System.out.println("Decrypted file saved to: " + outputFilePath);
            System.out.println("Time taken: " + (endTime - startTime) + " ms");
            
            // Display file sizes
            File encryptedFile = new File(encryptedFilePath);
            File decryptedFile = new File(outputFilePath);
            System.out.println("Original encrypted file size: " + encryptedFile.length() + " bytes");
            System.out.println("Decrypted file size: " + decryptedFile.length() + " bytes");
            
        } catch (Exception e) {
            System.err.println("Error during decryption: " + e.getMessage());
            e.printStackTrace();
            
            // Provide helpful error messages for common issues
            if (e.getMessage().contains("Secret key for message not found")) {
                System.err.println("\nPossible causes:");
                System.err.println("1. The private key doesn't match the public key used for encryption");
                System.err.println("2. The private key file is corrupted or in wrong format");
                System.err.println("3. The encrypted file was not encrypted for this key");
            } else if (e.getMessage().contains("checksum mismatch") || e.getMessage().contains("passphrase")) {
                System.err.println("\nPossible causes:");
                System.err.println("1. Incorrect passphrase for the private key");
                System.err.println("2. The private key file is corrupted");
            } else if (e.getMessage().contains("integrity check")) {
                System.err.println("\nThe file may have been corrupted during transmission or storage.");
            }
        } finally {
            scanner.close();
        }
    }
    
    /**
     * Generates an output filename based on the input encrypted filename.
     */
    private static String generateOutputFileName(String encryptedFilePath) {
        String fileName = new File(encryptedFilePath).getName();
        
        // Remove common PGP extensions
        if (fileName.endsWith(".pgp")) {
            return fileName.substring(0, fileName.length() - 4);
        } else if (fileName.endsWith(".gpg")) {
            return fileName.substring(0, fileName.length() - 4);
        } else if (fileName.endsWith(".asc")) {
            return fileName.substring(0, fileName.length() - 4);
        } else {
            return fileName + ".decrypted";
        }
    }
    
    /**
     * Prints usage information for command line usage.
     */
    public static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -cp target/classes com.example.pgp.PGPDecryptionExample");
        System.out.println("  java -cp target/classes com.example.pgp.PGPDecryptionExample <encrypted_file> <private_key> [passphrase] [output_file]");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Interactive mode");
        System.out.println("  java -cp target/classes com.example.pgp.PGPDecryptionExample");
        System.out.println();
        System.out.println("  # Command line mode");
        System.out.println("  java -cp target/classes com.example.pgp.PGPDecryptionExample encrypted.pgp private.key mypassword decrypted.txt");
        System.out.println();
        System.out.println("  # Without passphrase");
        System.out.println("  java -cp target/classes com.example.pgp.PGPDecryptionExample encrypted.pgp private.key \"\" decrypted.txt");
    }
}