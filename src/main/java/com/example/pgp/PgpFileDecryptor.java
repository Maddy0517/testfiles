package com.example.pgp;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder;
import org.bouncycastle.util.io.Streams;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.security.Security;
import java.util.Iterator;

/**
 * Utility for decrypting PGP-encrypted data using a private key (Bouncy Castle).
 */
public class PgpFileDecryptor {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Decrypt an encrypted byte array using the provided ASCII-armored private key and passphrase.
     */
    public byte[] decryptBytes(byte[] encryptedBytes, String privateKeyArmored, char[] passphrase) throws Exception {
        String normalizedKey = normalizeArmoredKey(privateKeyArmored);
        try (InputStream encryptedInputStream = new ByteArrayInputStream(encryptedBytes);
             InputStream privateKeyInputStream = new ByteArrayInputStream(normalizedKey.getBytes(StandardCharsets.UTF_8));
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            decryptFile(encryptedInputStream, privateKeyInputStream, passphrase, outputStream);
            return outputStream.toByteArray();
        } catch (Exception primary) {
            // Fallback: attempt base64 decode of the secret and retry
            try {
                byte[] decoded = Base64.getDecoder().decode(normalizedKey.replaceAll("\\s", ""));
                try (InputStream encryptedInputStream = new ByteArrayInputStream(encryptedBytes);
                     InputStream privateKeyInputStream = new ByteArrayInputStream(decoded);
                     ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    decryptFile(encryptedInputStream, privateKeyInputStream, passphrase, outputStream);
                    return outputStream.toByteArray();
                }
            } catch (Exception ignore) {
                throw primary;
            }
        }
    }

    /**
     * Decrypt a PGP encrypted stream using a private key.
     */
    public void decryptFile(InputStream encryptedInputStream,
                            InputStream privateKeyInputStream,
                            char[] passphrase,
                            OutputStream outputStream) throws Exception {

        // Load the private key ring
        PGPSecretKeyRingCollection secretKeyRings = new PGPSecretKeyRingCollection(
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

        PGPPrivateKey privateKey = null;
        PGPPublicKeyEncryptedData publicKeyEncryptedData = null;

        @SuppressWarnings("unchecked")
        Iterator<PGPEncryptedData> encryptedObjects = encryptedDataList.getEncryptedDataObjects();
        while (privateKey == null && encryptedObjects.hasNext()) {
            PGPEncryptedData encryptedData = encryptedObjects.next();
            if (encryptedData instanceof PGPPublicKeyEncryptedData) {
                publicKeyEncryptedData = (PGPPublicKeyEncryptedData) encryptedData;
                privateKey = findPrivateKey(secretKeyRings, publicKeyEncryptedData.getKeyID(), passphrase);
            }
        }

        if (privateKey == null || publicKeyEncryptedData == null) {
            throw new PGPException("Matching secret key for message not found.");
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
            JcaPGPObjectFactory compressedObjectFactory = new JcaPGPObjectFactory(compressedData.getDataStream());
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
     */
    private PGPPrivateKey findPrivateKey(PGPSecretKeyRingCollection secretKeyRings,
                                         long keyID,
                                         char[] passphrase) throws PGPException, IOException {
        PGPSecretKey secretKey = secretKeyRings.getSecretKey(keyID);
        if (secretKey == null) {
            return null;
        }
        return secretKey.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(passphrase));
    }

    private static String normalizeArmoredKey(String key) {
        if (key == null) return null;
        String s = key.trim();
        s = s.replace("\r\n", "\n").replace("\r", "\n");
        s = s.replace("\\n", "\n");
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }
}
