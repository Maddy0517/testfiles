import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utility class to encrypt files using RSA public key with AES hybrid encryption
 * Note: RSA can only encrypt small amounts of data, so we use AES for file content
 * and RSA to encrypt the AES key
 */
public class FileEncryptor {
    
    private static final String RSA_ALGORITHM = "RSA/ECB/PKCS1Padding";
    private static final String AES_ALGORITHM = "AES";
    private static final String AES_TRANSFORMATION = "AES/ECB/PKCS5Padding";
    
    public static void encryptFile(String inputFilePath, String publicKeyPath, String encryptedFilePath) 
            throws Exception {
        
        // Load public key
        PublicKey publicKey = loadPublicKey(publicKeyPath);
        
        // Generate AES key for file encryption
        KeyGenerator keyGenerator = KeyGenerator.getInstance(AES_ALGORITHM);
        keyGenerator.init(256);
        SecretKey aesKey = keyGenerator.generateKey();
        
        // Encrypt the file content with AES
        byte[] fileContent = Files.readAllBytes(Paths.get(inputFilePath));
        Cipher aesCipher = Cipher.getInstance(AES_TRANSFORMATION);
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey);
        byte[] encryptedContent = aesCipher.doFinal(fileContent);
        
        // Encrypt the AES key with RSA public key
        Cipher rsaCipher = Cipher.getInstance(RSA_ALGORITHM);
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedAESKey = rsaCipher.doFinal(aesKey.getEncoded());
        
        // Save encrypted file (format: [encrypted AES key length][encrypted AES key][encrypted content])
        try (FileOutputStream fos = new FileOutputStream(encryptedFilePath)) {
            // Write length of encrypted AES key (4 bytes)
            fos.write(intToByteArray(encryptedAESKey.length));
            // Write encrypted AES key
            fos.write(encryptedAESKey);
            // Write encrypted content
            fos.write(encryptedContent);
        }
        
        System.out.println("File encrypted successfully!");
        System.out.println("Input file: " + inputFilePath);
        System.out.println("Encrypted file: " + encryptedFilePath);
    }
    
    private static PublicKey loadPublicKey(String publicKeyPath) 
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] keyBytes = Files.readAllBytes(Paths.get(publicKeyPath));
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }
    
    private static byte[] intToByteArray(int value) {
        return new byte[] {
            (byte)(value >>> 24),
            (byte)(value >>> 16),
            (byte)(value >>> 8),
            (byte)value
        };
    }
    
    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java FileEncryptor <input_file> <public_key_file> <encrypted_output_file>");
            System.out.println("Example: java FileEncryptor example.txt public.key encrypted_file.enc");
            return;
        }
        
        try {
            encryptFile(args[0], args[1], args[2]);
        } catch (Exception e) {
            System.err.println("Error encrypting file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}