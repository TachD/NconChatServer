package sample;

import javax.crypto.*;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

public class Crypto {

    private final static String algorithm = "DES";
    private Key key = null;
    private Cipher cipher = null;

    public Crypto() throws NoSuchPaddingException, NoSuchAlgorithmException {
        cipher = Cipher.getInstance(algorithm);
    }

    public Key genKey() throws Exception {
        return key = KeyGenerator.getInstance(algorithm).generateKey();
    }

    public void setKey(Key key) {
        this.key = key;
    }
    //
    private byte[] encrypt(String input) throws InvalidKeyException,
            BadPaddingException, IllegalBlockSizeException {
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] inputBytes = input.getBytes();

        return cipher.doFinal(inputBytes);
    }

    private String decrypt(byte[] sendBytes, int count) throws InvalidKeyException,
            BadPaddingException, IllegalBlockSizeException {
        byte[] encryptionBytes = new byte[count];

        for (int i = 0; i < count; ++i)
            encryptionBytes[i] = sendBytes[i];

        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] recoveredBytes =
                cipher.doFinal(encryptionBytes);

        return new String(recoveredBytes);
    }

    public String readDecryptString(ObjectInputStream IS)
            throws Exception {
        byte[] EncrypterByte = new byte[126];
        int count = IS.read(EncrypterByte);

        return decrypt(EncrypterByte, count);
    }

    public void sendEncryptString(ObjectOutputStream OOS, final String Message)
            throws Exception {
        OOS.write(encrypt(Message));
        OOS.flush();
    }
}