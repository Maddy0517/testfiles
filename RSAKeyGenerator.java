import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

/**
 * Utility class to generate RSA key pairs and save them to files
 */
public class RSAKeyGenerator {
    
    private static final int KEY_SIZE = 2048;
    
    public static void generateKeyPair() throws NoSuchAlgorithmException, IOException {
        // Generate RSA key pair
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(KEY_SIZE, new SecureRandom());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();
        
        // Save public key to file
        try (FileOutputStream fos = new FileOutputStream("public.key")) {
            fos.write(publicKey.getEncoded());
        }
        
        // Save private key to file
        try (FileOutputStream fos = new FileOutputStream("private.key")) {
            fos.write(privateKey.getEncoded());
        }
        
        System.out.println("RSA key pair generated successfully!");
        System.out.println("Public key saved to: public.key");
        System.out.println("Private key saved to: private.key");
    }
    
    public static void main(String[] args) {
        try {
            generateKeyPair();
        } catch (Exception e) {
            System.err.println("Error generating key pair: " + e.getMessage());
            e.printStackTrace();
        }
    }
}