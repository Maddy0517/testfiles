package com.example.pgp;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;

import java.io.*;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Date;
import java.util.Iterator;

public class PgpFileEncryptor {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public void encryptFile(InputStream inputStream, OutputStream outputStream, 
                           InputStream privateKeyInputStream, char[] passphrase,
                           String fileName, boolean armor) throws Exception {

        // Get public key from private key
        PGPPublicKey publicKey = extractPublicKey(privateKeyInputStream);
        
        OutputStream encryptedOut = outputStream;
        if (armor) {
            encryptedOut = new ArmoredOutputStream(outputStream);
        }

        try {
            // Create encrypted data generator
            PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(
                new JcePGPDataEncryptorBuilder(PGPEncryptedData.AES_256)
                    .setWithIntegrityPacket(true)
                    .setSecureRandom(new SecureRandom())
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME));

            encGen.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(publicKey)
                .setProvider(BouncyCastleProvider.PROVIDER_NAME));

            OutputStream cOut = encGen.open(encryptedOut, new byte[4096]);

            // Create compressed data
            PGPCompressedDataGenerator compGen = new PGPCompressedDataGenerator(PGPCompressedData.ZIP);
            OutputStream compOut = compGen.open(cOut);

            // Create literal data
            PGPLiteralDataGenerator lGen = new PGPLiteralDataGenerator();
            OutputStream lOut = lGen.open(compOut, PGPLiteralData.BINARY, fileName, new Date(), new byte[4096]);

            // Copy input to output
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                lOut.write(buffer, 0, bytesRead);
            }

            lOut.close();
            lGen.close();
            compOut.close();
            compGen.close();
            cOut.close();
            encGen.close();

        } finally {
            if (armor) {
                encryptedOut.close();
            }
        }
    }

    private PGPPublicKey extractPublicKey(InputStream privateKeyInputStream) throws Exception {
        PGPSecretKeyRingCollection keyRings = new PGPSecretKeyRingCollection(
            PGPUtil.getDecoderStream(privateKeyInputStream),
            new JcaKeyFingerprintCalculator());

        Iterator<PGPSecretKeyRing> ringIterator = keyRings.getKeyRings();
        while (ringIterator.hasNext()) {
            PGPSecretKeyRing keyRing = ringIterator.next();
            Iterator<PGPSecretKey> keyIterator = keyRing.getSecretKeys();
            
            while (keyIterator.hasNext()) {
                PGPSecretKey secretKey = keyIterator.next();
                PGPPublicKey publicKey = secretKey.getPublicKey();
                
                if (publicKey.isEncryptionKey()) {
                    return publicKey;
                }
            }
        }
        
        throw new IllegalArgumentException("No encryption key found");
    }
}