package com.example.pgp;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;

import java.io.*;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Date;
import java.util.Iterator;

/**
 * PGP file encryption utility using Bouncy Castle library.
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
     * Encrypt a file using a PGP public key extracted from a private key.
     *
     * @param inputStream           Input stream of the file to encrypt
     * @param outputStream          Output stream for the encrypted file
     * @param privateKeyInputStream Input stream of the private key (to extract public key)
     * @param passphrase           Passphrase for the private key
     * @param fileName             Original filename (for metadata)
     * @param armor                Whether to create ASCII-armored output
     * @throws Exception if encryption fails
     */
    public void encryptFile(InputStream inputStream,
                           OutputStream outputStream,
                           InputStream privateKeyInputStream,
                           char[] passphrase,
                           String fileName,
                           boolean armor) throws Exception {

        // Load the private key ring to extract public key
        PGPSecretKeyRingCollection secretKeyRings = 
            new PGPSecretKeyRingCollection(
                PGPUtil.getDecoderStream(privateKeyInputStream),
                new JcaKeyFingerprintCalculator());

        // Find the first encryption-capable key
        PGPPublicKey publicKey = null;
        Iterator<PGPSecretKeyRing> keyRings = secretKeyRings.getKeyRings();
        
        while (keyRings.hasNext() && publicKey == null) {
            PGPSecretKeyRing keyRing = keyRings.next();
            Iterator<PGPSecretKey> keys = keyRing.getSecretKeys();
            
            while (keys.hasNext()) {
                PGPSecretKey secretKey = keys.next();
                PGPPublicKey pubKey = secretKey.getPublicKey();
                
                if (pubKey.isEncryptionKey()) {
                    publicKey = pubKey;
                    break;
                }
            }
        }

        if (publicKey == null) {
            throw new IllegalArgumentException("No encryption-capable public key found in the key ring.");
        }

        encryptFile(inputStream, outputStream, publicKey, fileName, armor);
    }

    /**
     * Encrypt a file using a PGP public key.
     *
     * @param inputStream  Input stream of the file to encrypt
     * @param outputStream Output stream for the encrypted file
     * @param publicKey    PGP public key for encryption
     * @param fileName     Original filename (for metadata)
     * @param armor        Whether to create ASCII-armored output
     * @throws Exception if encryption fails
     */
    public void encryptFile(InputStream inputStream,
                           OutputStream outputStream,
                           PGPPublicKey publicKey,
                           String fileName,
                           boolean armor) throws Exception {

        OutputStream encryptedOut = outputStream;
        
        if (armor) {
            encryptedOut = new ArmoredOutputStream(outputStream);
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

            OutputStream cOut = encryptedDataGenerator.open(encryptedOut, new byte[4096]);

            // Create compressed data generator
            PGPCompressedDataGenerator compressedDataGenerator = 
                new PGPCompressedDataGenerator(PGPCompressedData.ZIP);
            
            OutputStream compressedOut = compressedDataGenerator.open(cOut);

            // Create literal data generator
            PGPLiteralDataGenerator literalDataGenerator = new PGPLiteralDataGenerator();
            
            OutputStream literalOut = literalDataGenerator.open(
                compressedOut,
                PGPLiteralData.BINARY,
                fileName,
                new Date(),
                new byte[4096]);

            // Copy input data to encrypted output
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                literalOut.write(buffer, 0, bytesRead);
            }

            // Close all streams in reverse order
            literalOut.close();
            literalDataGenerator.close();
            compressedOut.close();
            compressedDataGenerator.close();
            cOut.close();
            encryptedDataGenerator.close();

        } finally {
            if (armor) {
                encryptedOut.close();
            }
        }
    }

    /**
     * Extract public key from a private key stream.
     *
     * @param privateKeyInputStream Input stream of the private key
     * @param passphrase           Passphrase for the private key
     * @return PGPPublicKey extracted from the private key
     * @throws Exception if key extraction fails
     */
    public PGPPublicKey extractPublicKey(InputStream privateKeyInputStream, 
                                        char[] passphrase) throws Exception {
        
        PGPSecretKeyRingCollection secretKeyRings = 
            new PGPSecretKeyRingCollection(
                PGPUtil.getDecoderStream(privateKeyInputStream),
                new JcaKeyFingerprintCalculator());

        Iterator<PGPSecretKeyRing> keyRings = secretKeyRings.getKeyRings();
        
        while (keyRings.hasNext()) {
            PGPSecretKeyRing keyRing = keyRings.next();
            Iterator<PGPSecretKey> keys = keyRing.getSecretKeys();
            
            while (keys.hasNext()) {
                PGPSecretKey secretKey = keys.next();
                PGPPublicKey publicKey = secretKey.getPublicKey();
                
                if (publicKey.isEncryptionKey()) {
                    return publicKey;
                }
            }
        }
        
        throw new IllegalArgumentException("No encryption-capable public key found in the key ring.");
    }
}