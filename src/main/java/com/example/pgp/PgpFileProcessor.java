package com.example.pgp;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;
import org.bouncycastle.util.io.Streams;

import java.io.*;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Date;
import java.util.Iterator;
import java.util.logging.Logger;

/**
 * PGP file processor for Google Cloud Functions.
 * Handles both decryption and encryption of PGP files.
 */
public class PgpFileProcessor {
    
    private static final Logger logger = Logger.getLogger(PgpFileProcessor.class.getName());

    static {
        // Add Bouncy Castle security provider
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
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

        logger.info("Starting PGP decryption process");

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
            throw new IllegalArgumentException("Secret key for message not found.");
        }

        logger.info("Private key found, decrypting data");

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

        logger.info("PGP decryption completed successfully");
    }

    /**
     * Encrypt data using PGP public key.
     *
     * @param inputStream      Input stream of data to encrypt
     * @param publicKeyStream  Input stream of public key
     * @param outputStream     Output stream for encrypted data
     * @param fileName         Name of the file being encrypted
     * @throws Exception if encryption fails
     */
    public void encryptFile(InputStream inputStream, 
                           InputStream publicKeyStream,
                           OutputStream outputStream,
                           String fileName) throws Exception {

        logger.info("Starting PGP encryption process for file: " + fileName);

        // Load public key
        PGPPublicKeyRingCollection publicKeyRings = 
            new PGPPublicKeyRingCollection(
                PGPUtil.getDecoderStream(publicKeyStream),
                new JcaKeyFingerprintCalculator());

        PGPPublicKey publicKey = findEncryptionKey(publicKeyRings);
        if (publicKey == null) {
            throw new IllegalArgumentException("No encryption key found in public key ring");
        }

        // Create encrypted data generator
        PGPEncryptedDataGenerator encryptedDataGenerator = new PGPEncryptedDataGenerator(
            new JcePGPDataEncryptorBuilder(PGPEncryptedData.AES_256)
                .setWithIntegrityPacket(true)
                .setSecureRandom(new SecureRandom())
                .setProvider(BouncyCastleProvider.PROVIDER_NAME));

        encryptedDataGenerator.addMethod(
            new JcePublicKeyKeyEncryptionMethodGenerator(publicKey)
                .setProvider(BouncyCastleProvider.PROVIDER_NAME));

        // Create literal data generator
        PGPLiteralDataGenerator literalDataGenerator = new PGPLiteralDataGenerator();

        // Create compressed data generator
        PGPCompressedDataGenerator compressedDataGenerator = 
            new PGPCompressedDataGenerator(PGPCompressedData.ZIP);

        try (OutputStream encryptedOut = encryptedDataGenerator.open(outputStream, new byte[4096]);
             OutputStream compressedOut = compressedDataGenerator.open(encryptedOut);
             OutputStream literalOut = literalDataGenerator.open(compressedOut, 
                                                               PGPLiteralData.BINARY,
                                                               fileName,
                                                               new Date(),
                                                               new byte[4096])) {
            
            Streams.pipeAll(inputStream, literalOut);
        }

        logger.info("PGP encryption completed successfully");
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

    /**
     * Find a suitable encryption key from the public key ring.
     *
     * @param publicKeyRings Collection of public key rings
     * @return PGPPublicKey suitable for encryption, null if not found
     */
    private PGPPublicKey findEncryptionKey(PGPPublicKeyRingCollection publicKeyRings) {
        Iterator<PGPPublicKeyRing> keyRingIterator = publicKeyRings.getKeyRings();
        
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
        
        return null;
    }
}