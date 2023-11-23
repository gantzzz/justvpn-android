package online.justvpn.JCrypto;

import android.util.Log;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class JCrypto {
    byte[] m_PubKeyBytes;
    byte[] m_EncryptedSessionKeyBytes = null;

    byte[] m_UnencryptedSessionKeyBytes = null;
    byte[] m_UnencryptedIV = null;

    byte[] m_EncryptedIV = null;
    SecretKey mSessionKey = null;


    boolean m_bEnabled = false;

    public JCrypto(byte[] pKey)
    {
        m_PubKeyBytes = pKey;
    }
    public boolean GenerateSessionKey()
    {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(m_PubKeyBytes);
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            // Get session key
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            SecretKey sessionKey = keyGen.generateKey();

            // Encrypt the session key using the public key
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            m_UnencryptedSessionKeyBytes = sessionKey.getEncoded();
            m_EncryptedSessionKeyBytes = cipher.doFinal(m_UnencryptedSessionKeyBytes);

            mSessionKey = new SecretKeySpec(m_UnencryptedSessionKeyBytes, 0, m_UnencryptedSessionKeyBytes.length, "AES");
        } catch (Exception e) {
            return false;
        }
        GenerateIV();
        return true;
    }

    private boolean GenerateIV()
    {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(m_PubKeyBytes);
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            // Encrypt the session key using the public key
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);

            // Generate IV for AES encryption
            SecureRandom random = new SecureRandom();
            byte[] iv = new byte[16]; // 16 bytes for AES
            random.nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            m_UnencryptedIV = ivSpec.getIV();

            // encrypt IV
            m_EncryptedIV = cipher.doFinal(m_UnencryptedIV);
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    public byte[] AESEncrypt(byte[] bytes)
    {
        byte[] encrypted = null;

        try {
            IvParameterSpec ivSpec = new IvParameterSpec(m_UnencryptedIV);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            cipher.init(Cipher.ENCRYPT_MODE, mSessionKey, ivSpec);

            encrypted = cipher.doFinal(bytes);

        } catch (Exception e)
        {
            Log.d("Justvpn", "Encrypt failed: " + e.toString());
            return encrypted;
        }

        return encrypted;
    }

    public byte[] AESDecrypt(byte[] bytes)
    {
        byte[] decrypted = null;

        try {
            IvParameterSpec ivSpec = new IvParameterSpec(m_UnencryptedIV);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            cipher.init(Cipher.DECRYPT_MODE, mSessionKey, ivSpec);

            decrypted = cipher.doFinal(bytes);

        } catch (Exception e)
        {
            Log.d("Justvpn", "Decrypt failed: " + e.toString());
            return decrypted;
        }

        return decrypted;
    }

    public byte[] GetEncryptedSessionKey()
    {
        return m_EncryptedSessionKeyBytes;
    }

    public byte[] GetEncryptedIV()
    {
        return m_EncryptedIV;
    }

    public boolean IsEncryptionEnabled()
    {
        return m_bEnabled;
    }

    public void SetEncryptionEnabled(boolean bEnabled)
    {
        m_bEnabled = bEnabled;
    }
}
