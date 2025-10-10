import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utility class to decrypt files using RSA private key
 * This class decrypts files that were encrypted using the FileEncryptor class
 */
public class FileDecryptor {
    
    private static final String RSA_ALGORITHM = "RSA/ECB/PKCS1Padding";
    private static final String AES_ALGORITHM = "AES";
    private static final String AES_TRANSFORMATION = "AES/ECB/PKCS5Padding";
    
    /**
     * Decrypts a file that was encrypted with RSA public key using hybrid encryption
     * 
     * @param encryptedFilePath Path to the encrypted file
     * @param privateKeyPath Path to the RSA private key file
     * @param outputFilePath Path where the decrypted file will be saved
     */
    public static void decryptFile(String encryptedFilePath, String privateKeyPath, String outputFilePath) 
            throws Exception {
        
        System.out.println("Starting file decryption...");
        
        // Load private key
        PrivateKey privateKey = loadPrivateKey(privateKeyPath);
        System.out.println("Private key loaded successfully");
        
        // Read encrypted file
        byte[] encryptedData = Files.readAllBytes(Paths.get(encryptedFilePath));
        System.out.println("Encrypted file read: " + encryptedData.length + " bytes");
        
        // Extract encrypted AES key length (first 4 bytes)
        int encryptedKeyLength = byteArrayToInt(encryptedData, 0);
        System.out.println("Encrypted AES key length: " + encryptedKeyLength + " bytes");
        
        // Extract encrypted AES key
        byte[] encryptedAESKey = new byte[encryptedKeyLength];
        System.arraycopy(encryptedData, 4, encryptedAESKey, 0, encryptedKeyLength);
        
        // Extract encrypted content
        int contentLength = encryptedData.length - 4 - encryptedKeyLength;
        byte[] encryptedContent = new byte[contentLength];
        System.arraycopy(encryptedData, 4 + encryptedKeyLength, encryptedContent, 0, contentLength);
        System.out.println("Encrypted content length: " + contentLength + " bytes");
        
        // Decrypt AES key using RSA private key
        Cipher rsaCipher = Cipher.getInstance(RSA_ALGORITHM);
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decryptedAESKey = rsaCipher.doFinal(encryptedAESKey);
        System.out.println("AES key decrypted successfully");
        
        // Recreate AES key
        SecretKeySpec aesKey = new SecretKeySpec(decryptedAESKey, AES_ALGORITHM);
        
        // Decrypt file content using AES key
        Cipher aesCipher = Cipher.getInstance(AES_TRANSFORMATION);
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey);
        byte[] decryptedContent = aesCipher.doFinal(encryptedContent);
        System.out.println("File content decrypted successfully");
        
        // Save decrypted content to output file
        Files.write(Paths.get(outputFilePath), decryptedContent);
        
        System.out.println("File decrypted successfully!");
        System.out.println("Encrypted file: " + encryptedFilePath);
        System.out.println("Decrypted file: " + outputFilePath);
        System.out.println("Decrypted file size: " + decryptedContent.length + " bytes");
    }
    
    /**
     * Loads an RSA private key from a file
     */
    private static PrivateKey loadPrivateKey(String privateKeyPath) 
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        System.out.println("Loading private key from: " + privateKeyPath);
        byte[] keyBytes = Files.readAllBytes(Paths.get(privateKeyPath));
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(spec);
    }
    
    /**
     * Converts byte array to integer (big-endian)
     */
    private static int byteArrayToInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) |
               ((bytes[offset + 1] & 0xFF) << 16) |
               ((bytes[offset + 2] & 0xFF) << 8) |
               (bytes[offset + 3] & 0xFF);
    }
    
    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java FileDecryptor <encrypted_file> <private_key_file> <decrypted_output_file>");
            System.out.println("Example: java FileDecryptor encrypted_file.enc private.key decrypted_file.txt");
            return;
        }
        
        try {
            decryptFile(args[0], args[1], args[2]);
        } catch (Exception e) {
            System.err.println("Error decrypting file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}