package com.example.pgp;

import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder;
import org.bouncycastle.util.io.Streams;

import java.io.*;
import java.security.Security;
import java.util.Iterator;

/**
 * PGP Decryptor utility class for decrypting PGP-encrypted files using a private key.
 * This class uses Bouncy Castle library for PGP operations.
 */
public class PGPDecryptor {
    
    static {
        // Add Bouncy Castle as a security provider
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
    
    /**
     * Decrypts a PGP-encrypted file using the provided private key.
     * 
     * @param encryptedFilePath Path to the encrypted file
     * @param privateKeyPath Path to the private key file
     * @param passphrase Passphrase for the private key (can be null if no passphrase)
     * @param outputFilePath Path where the decrypted file will be saved
     * @throws Exception if decryption fails
     */
    public static void decryptFile(String encryptedFilePath, String privateKeyPath, 
                                 String passphrase, String outputFilePath) throws Exception {
        
        try (InputStream encryptedInput = new FileInputStream(encryptedFilePath);
             InputStream privateKeyInput = new FileInputStream(privateKeyPath);
             OutputStream decryptedOutput = new FileOutputStream(outputFilePath)) {
            
            decryptFile(encryptedInput, privateKeyInput, passphrase, decryptedOutput);
        }
    }
    
    /**
     * Decrypts a PGP-encrypted stream using the provided private key stream.
     * 
     * @param encryptedInput Input stream of encrypted data
     * @param privateKeyInput Input stream of private key
     * @param passphrase Passphrase for the private key (can be null if no passphrase)
     * @param decryptedOutput Output stream for decrypted data
     * @throws Exception if decryption fails
     */
    public static void decryptFile(InputStream encryptedInput, InputStream privateKeyInput, 
                                 String passphrase, OutputStream decryptedOutput) throws Exception {
        
        // Load the private key
        PGPSecretKeyRingCollection secretKeyRings = loadSecretKeyRing(privateKeyInput);
        
        // Prepare the encrypted input stream
        InputStream decodedInput = PGPUtil.getDecoderStream(encryptedInput);
        
        // Parse the encrypted data
        PGPObjectFactory pgpFactory = new PGPObjectFactory(decodedInput, new JcaKeyFingerprintCalculator());
        Object obj = pgpFactory.nextObject();
        
        // Handle the encrypted data list
        PGPEncryptedDataList encryptedDataList;
        if (obj instanceof PGPEncryptedDataList) {
            encryptedDataList = (PGPEncryptedDataList) obj;
        } else {
            encryptedDataList = (PGPEncryptedDataList) pgpFactory.nextObject();
        }
        
        // Find the correct encrypted data packet
        PGPPrivateKey privateKey = null;
        PGPPublicKeyEncryptedData publicKeyEncryptedData = null;
        
        Iterator<PGPEncryptedData> encryptedDataIterator = encryptedDataList.getEncryptedDataObjects();
        while (encryptedDataIterator.hasNext()) {
            PGPEncryptedData encryptedData = encryptedDataIterator.next();
            
            if (encryptedData instanceof PGPPublicKeyEncryptedData) {
                PGPPublicKeyEncryptedData pkEncryptedData = (PGPPublicKeyEncryptedData) encryptedData;
                privateKey = findSecretKey(secretKeyRings, pkEncryptedData.getKeyID(), passphrase);
                
                if (privateKey != null) {
                    publicKeyEncryptedData = pkEncryptedData;
                    break;
                }
            }
        }
        
        if (privateKey == null) {
            throw new IllegalArgumentException("Secret key for message not found.");
        }
        
        // Decrypt the data
        try (InputStream decryptedInputStream = publicKeyEncryptedData.getDataStream(
                new JcePublicKeyDataDecryptorFactoryBuilder()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(privateKey))) {
            
            PGPObjectFactory decryptedFactory = new PGPObjectFactory(decryptedInputStream, 
                                                                   new JcaKeyFingerprintCalculator());
            Object decryptedObj = decryptedFactory.nextObject();
            
            if (decryptedObj instanceof PGPCompressedData) {
                PGPCompressedData compressedData = (PGPCompressedData) decryptedObj;
                PGPObjectFactory compressedFactory = new PGPObjectFactory(compressedData.getDataStream(), 
                                                                         new JcaKeyFingerprintCalculator());
                decryptedObj = compressedFactory.nextObject();
            }
            
            if (decryptedObj instanceof PGPLiteralData) {
                PGPLiteralData literalData = (PGPLiteralData) decryptedObj;
                try (InputStream literalInputStream = literalData.getInputStream()) {
                    Streams.pipeAll(literalInputStream, decryptedOutput);
                }
            } else {
                throw new PGPException("Message is not a simple encrypted file - type unknown.");
            }
        }
        
        // Verify integrity if present
        if (publicKeyEncryptedData.isIntegrityProtected()) {
            if (!publicKeyEncryptedData.verify()) {
                throw new PGPException("Message failed integrity check");
            }
        }
    }
    
    /**
     * Loads the secret key ring from an input stream.
     */
    private static PGPSecretKeyRingCollection loadSecretKeyRing(InputStream privateKeyInput) throws Exception {
        InputStream decodedInput = PGPUtil.getDecoderStream(privateKeyInput);
        return new PGPSecretKeyRingCollection(decodedInput, new JcaKeyFingerprintCalculator());
    }
    
    /**
     * Finds the secret key for the given key ID and decrypts it with the passphrase.
     */
    private static PGPPrivateKey findSecretKey(PGPSecretKeyRingCollection secretKeyRings, 
                                             long keyID, String passphrase) throws Exception {
        PGPSecretKey secretKey = secretKeyRings.getSecretKey(keyID);
        
        if (secretKey == null) {
            return null;
        }
        
        char[] passphraseChars = passphrase != null ? passphrase.toCharArray() : new char[0];
        
        return secretKey.extractPrivateKey(
            new JcePBESecretKeyDecryptorBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(passphraseChars)
        );
    }
    
    /**
     * Utility method to check if a file is armored (ASCII-armored PGP format).
     */
    public static boolean isArmoredFile(String filePath) throws IOException {
        try (InputStream input = new FileInputStream(filePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            
            String firstLine = reader.readLine();
            return firstLine != null && firstLine.startsWith("-----BEGIN PGP MESSAGE-----");
        }
    }
    
    /**
     * Utility method to get file information from encrypted PGP data.
     */
    public static String getOriginalFileName(String encryptedFilePath) throws Exception {
        try (InputStream encryptedInput = new FileInputStream(encryptedFilePath)) {
            return getOriginalFileName(encryptedInput);
        }
    }
    
    /**
     * Utility method to get file information from encrypted PGP data stream.
     */
    public static String getOriginalFileName(InputStream encryptedInput) throws Exception {
        InputStream decodedInput = PGPUtil.getDecoderStream(encryptedInput);
        PGPObjectFactory pgpFactory = new PGPObjectFactory(decodedInput, new JcaKeyFingerprintCalculator());
        
        Object obj = pgpFactory.nextObject();
        if (!(obj instanceof PGPEncryptedDataList)) {
            obj = pgpFactory.nextObject();
        }
        
        // Note: This method would require decryption to get the original filename
        // For now, return null as filename extraction requires the private key
        return null;
    }
}