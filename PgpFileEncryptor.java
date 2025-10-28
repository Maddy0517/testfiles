package com.example.pgp;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;

import java.io.*;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Date;
import java.util.Iterator;

/**
 * Simple PGP file encryption utility using Bouncy Castle library.
 * This class provides methods to encrypt files using a PGP public key.
 */
public class PgpFileEncryptor {

    static {
        // Add Bouncy Castle security provider
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Encrypt data using a PGP public key.
     *
     * @param inputData       Byte array of data to encrypt
     * @param publicKeyString Public key as a string
     * @param fileName        Name of the file being encrypted
     * @param armor           Whether to use ASCII armor encoding
     * @return Encrypted data as byte array
     * @throws Exception if encryption fails
     */
    public byte[] encryptData(byte[] inputData, String publicKeyString, 
                             String fileName, boolean armor) throws Exception {
        
        ByteArrayInputStream keyInputStream = new ByteArrayInputStream(publicKeyString.getBytes());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        encryptData(inputData, keyInputStream, outputStream, fileName, armor);
        
        return outputStream.toByteArray();
    }

    /**
     * Encrypt data using a PGP public key from an input stream.
     *
     * @param inputData        Byte array of data to encrypt
     * @param publicKeyStream  Input stream containing the public key
     * @param outputStream     Output stream for encrypted data
     * @param fileName         Name of the file being encrypted
     * @param armor            Whether to use ASCII armor encoding
     * @throws Exception if encryption fails
     */
    public void encryptData(byte[] inputData, InputStream publicKeyStream,
                           OutputStream outputStream, String fileName, 
                           boolean armor) throws Exception {

        // Read the public key
        PGPPublicKey publicKey = readPublicKey(publicKeyStream);

        // Create output stream with optional ASCII armor
        OutputStream out = outputStream;
        if (armor) {
            out = new ArmoredOutputStream(out);
        }

        try {
            // Create encrypted data generator
            PGPEncryptedDataGenerator encryptedDataGenerator = new PGPEncryptedDataGenerator(
                new JcePGPDataEncryptorBuilder(PGPEncryptedData.AES_256)
                    .setWithIntegrityPacket(true)
                    .setSecureRandom(new SecureRandom())
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME));

            encryptedDataGenerator.addMethod(
                new JcePublicKeyKeyEncryptionMethodGenerator(publicKey)
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME));

            OutputStream encryptedOut = encryptedDataGenerator.open(out, new byte[4096]);

            // Create compressed data generator
            PGPCompressedDataGenerator compressedDataGenerator = 
                new PGPCompressedDataGenerator(PGPCompressedData.ZIP);
            
            OutputStream compressedOut = compressedDataGenerator.open(encryptedOut);

            // Create literal data generator
            PGPLiteralDataGenerator literalDataGenerator = new PGPLiteralDataGenerator();
            OutputStream literalOut = literalDataGenerator.open(
                compressedOut,
                PGPLiteralData.BINARY,
                fileName,
                inputData.length,
                new Date());

            // Write the data
            literalOut.write(inputData);

            // Close all streams in reverse order
            literalOut.close();
            literalDataGenerator.close();
            compressedOut.close();
            compressedDataGenerator.close();
            encryptedOut.close();
            encryptedDataGenerator.close();

            if (armor) {
                out.close();
            }

        } catch (Exception e) {
            throw new Exception("Error during encryption: " + e.getMessage(), e);
        }
    }

    /**
     * Read a PGP public key from an input stream.
     *
     * @param inputStream Input stream containing the public key
     * @return PGPPublicKey object
     * @throws Exception if key cannot be read
     */
    private PGPPublicKey readPublicKey(InputStream inputStream) throws Exception {
        InputStream decoderStream = PGPUtil.getDecoderStream(inputStream);
        
        PGPPublicKeyRingCollection keyRingCollection = 
            new PGPPublicKeyRingCollection(decoderStream, new JcaKeyFingerprintCalculator());

        // Iterate through key rings to find an encryption key
        Iterator<PGPPublicKeyRing> keyRingIterator = keyRingCollection.getKeyRings();
        while (keyRingIterator.hasNext()) {
            PGPPublicKeyRing keyRing = keyRingIterator.next();
            Iterator<PGPPublicKey> keyIterator = keyRing.getPublicKeys();
            
            while (keyIterator.hasNext()) {
                PGPPublicKey key = keyIterator.next();
                
                if (key.isEncryptionKey()) {
                    return key;
                }
            }
        }

        throw new IllegalArgumentException("Can't find encryption key in key ring.");
    }

    /**
     * Extract public key from a private key string.
     * This method reads a private key and extracts its corresponding public key.
     *
     * @param privateKeyString Private key as a string
     * @param passphrase       Passphrase for the private key (can be null if not encrypted)
     * @return Public key as a string in ASCII armor format
     * @throws Exception if key extraction fails
     */
    public String extractPublicKeyFromPrivateKey(String privateKeyString, String passphrase) 
            throws Exception {
        
        ByteArrayInputStream keyInputStream = new ByteArrayInputStream(privateKeyString.getBytes());
        InputStream decoderStream = PGPUtil.getDecoderStream(keyInputStream);
        
        PGPSecretKeyRingCollection secretKeyRings = 
            new PGPSecretKeyRingCollection(decoderStream, new JcaKeyFingerprintCalculator());

        Iterator<PGPSecretKeyRing> keyRingIterator = secretKeyRings.getKeyRings();
        while (keyRingIterator.hasNext()) {
            PGPSecretKeyRing keyRing = keyRingIterator.next();
            Iterator<PGPSecretKey> keyIterator = keyRing.getSecretKeys();
            
            while (keyIterator.hasNext()) {
                PGPSecretKey secretKey = keyIterator.next();
                PGPPublicKey publicKey = secretKey.getPublicKey();
                
                if (publicKey.isEncryptionKey()) {
                    // Export public key to string
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    ArmoredOutputStream armoredOut = new ArmoredOutputStream(outputStream);
                    publicKey.encode(armoredOut);
                    armoredOut.close();
                    
                    return outputStream.toString();
                }
            }
        }

        throw new IllegalArgumentException("Can't find encryption key in private key ring.");
    }
}
