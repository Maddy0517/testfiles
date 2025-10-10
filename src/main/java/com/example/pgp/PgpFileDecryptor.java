package com.example.pgp;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder;
import org.bouncycastle.util.io.Streams;

import java.io.*;
import java.security.Security;
import java.util.Iterator;

/**
 * Simple PGP file decryption utility using Bouncy Castle library.
 * This class provides methods to decrypt PGP encrypted files using a private key.
 */
public class PgpFileDecryptor {

    static {
        // Add Bouncy Castle security provider
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Decrypt a PGP encrypted file using a private key.
     *
     * @param encryptedFile    Path to the encrypted file
     * @param privateKeyFile   Path to the private key file
     * @param passphrase       Passphrase for the private key
     * @param outputFile       Path where the decrypted file will be saved
     * @throws Exception if decryption fails
     */
    public void decryptFile(String encryptedFile, String privateKeyFile, 
                           String passphrase, String outputFile) throws Exception {
        
        try (InputStream keyInputStream = new FileInputStream(privateKeyFile);
             InputStream encryptedInputStream = new FileInputStream(encryptedFile);
             OutputStream outputStream = new FileOutputStream(outputFile)) {
            
            decryptFile(encryptedInputStream, keyInputStream, 
                       passphrase.toCharArray(), outputStream);
        }
    }

    /**
     * Decrypt a PGP encrypted stream using a private key.
     *
     * @param encryptedInputStream  Input stream of encrypted data
     * @param privateKeyInputStream Input stream of private key
     * @param passphrase           Passphrase for the private key
     * @param outputStream         Output stream for decrypted data
     * @throws Exception if decryption fails
     */
    public void decryptFile(InputStream encryptedInputStream, 
                           InputStream privateKeyInputStream,
                           char[] passphrase, 
                           OutputStream outputStream) throws Exception {

        // Load the private key ring
        PGPSecretKeyRingCollection secretKeyRings = 
            new PGPSecretKeyRingCollection(
                PGPUtil.getDecoderStream(privateKeyInputStream),
                new JcaKeyFingerprintCalculator());

        // Parse the encrypted data
        InputStream decoderStream = PGPUtil.getDecoderStream(encryptedInputStream);
        JcaPGPObjectFactory objectFactory = new JcaPGPObjectFactory(decoderStream);

        Object obj = objectFactory.nextObject();
        PGPEncryptedDataList encryptedDataList;

        if (obj instanceof PGPEncryptedDataList) {
            encryptedDataList = (PGPEncryptedDataList) obj;
        } else {
            encryptedDataList = (PGPEncryptedDataList) objectFactory.nextObject();
        }

        // Find the encrypted data packet that matches our private key
        PGPPrivateKey privateKey = null;
        PGPPublicKeyEncryptedData publicKeyEncryptedData = null;

        Iterator<PGPEncryptedData> encryptedObjects = encryptedDataList.getEncryptedDataObjects();
        while (privateKey == null && encryptedObjects.hasNext()) {
            PGPEncryptedData encryptedData = encryptedObjects.next();
            
            if (encryptedData instanceof PGPPublicKeyEncryptedData) {
                publicKeyEncryptedData = (PGPPublicKeyEncryptedData) encryptedData;
                privateKey = findPrivateKey(secretKeyRings, 
                                          publicKeyEncryptedData.getKeyID(), 
                                          passphrase);
            }
        }

        if (privateKey == null) {
            throw new IllegalArgumentException(
                "Secret key for message not found.");
        }

        // Decrypt the data
        InputStream clearTextStream = publicKeyEncryptedData.getDataStream(
            new JcePublicKeyDataDecryptorFactoryBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(privateKey));

        JcaPGPObjectFactory clearObjectFactory = new JcaPGPObjectFactory(clearTextStream);
        Object message = clearObjectFactory.nextObject();

        if (message instanceof PGPCompressedData) {
            PGPCompressedData compressedData = (PGPCompressedData) message;
            JcaPGPObjectFactory compressedObjectFactory = 
                new JcaPGPObjectFactory(compressedData.getDataStream());
            message = compressedObjectFactory.nextObject();
        }

        if (message instanceof PGPLiteralData) {
            PGPLiteralData literalData = (PGPLiteralData) message;
            Streams.pipeAll(literalData.getInputStream(), outputStream);
        } else {
            throw new PGPException("Message is not a simple encrypted file - type unknown.");
        }

        // Verify integrity
        if (publicKeyEncryptedData.isIntegrityProtected()) {
            if (!publicKeyEncryptedData.verify()) {
                throw new PGPException("Message failed integrity check");
            }
        }
    }

    /**
     * Find the private key that matches the given key ID.
     *
     * @param secretKeyRings Collection of secret key rings
     * @param keyID          Key ID to search for
     * @param passphrase     Passphrase for the private key
     * @return PGPPrivateKey if found, null otherwise
     * @throws Exception if key extraction fails
     */
    private PGPPrivateKey findPrivateKey(PGPSecretKeyRingCollection secretKeyRings,
                                        long keyID, 
                                        char[] passphrase) throws Exception {
        PGPSecretKey secretKey = secretKeyRings.getSecretKey(keyID);

        if (secretKey == null) {
            return null;
        }

        return secretKey.extractPrivateKey(
            new JcePBESecretKeyDecryptorBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(passphrase));
    }
}