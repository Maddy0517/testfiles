/**
 * RSA File Encryption/Decryption Demo
 * 
 * This class demonstrates the complete workflow of:
 * 1. Generating RSA key pairs
 * 2. Encrypting a file with public key
 * 3. Decrypting the file with private key
 */
public class RSAFileDemo {
    
    public static void main(String[] args) {
        try {
            System.out.println("=== RSA File Encryption/Decryption Demo ===\n");
            
            // Step 1: Generate RSA key pair
            System.out.println("Step 1: Generating RSA key pair...");
            RSAKeyGenerator.generateKeyPair();
            System.out.println();
            
            // Step 2: Create a sample file to encrypt
            String originalFile = "sample.txt";
            String encryptedFile = "sample_encrypted.enc";
            String decryptedFile = "sample_decrypted.txt";
            
            System.out.println("Step 2: Creating sample file...");
            java.nio.file.Files.write(
                java.nio.file.Paths.get(originalFile), 
                "Hello World! This is a secret message that will be encrypted with RSA.\nThis demonstrates file encryption and decryption using RSA keys.".getBytes()
            );
            System.out.println("Sample file created: " + originalFile);
            System.out.println();
            
            // Step 3: Encrypt the file
            System.out.println("Step 3: Encrypting file with public key...");
            FileEncryptor.encryptFile(originalFile, "public.key", encryptedFile);
            System.out.println();
            
            // Step 4: Decrypt the file
            System.out.println("Step 4: Decrypting file with private key...");
            FileDecryptor.decryptFile(encryptedFile, "private.key", decryptedFile);
            System.out.println();
            
            // Step 5: Verify the decryption
            System.out.println("Step 5: Verifying decryption...");
            String originalContent = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(originalFile)));
            String decryptedContent = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(decryptedFile)));
            
            if (originalContent.equals(decryptedContent)) {
                System.out.println("✅ SUCCESS: Decryption successful! Original and decrypted files match.");
            } else {
                System.out.println("❌ ERROR: Decryption failed! Files do not match.");
            }
            
            System.out.println("\nOriginal content:");
            System.out.println(originalContent);
            System.out.println("\nDecrypted content:");
            System.out.println(decryptedContent);
            
        } catch (Exception e) {
            System.err.println("Error in demo: " + e.getMessage());
            e.printStackTrace();
        }
    }
}