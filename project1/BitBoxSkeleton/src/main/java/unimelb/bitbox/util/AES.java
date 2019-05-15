package unimelb.bitbox.util;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Select AES/ECB/PKCS5Padding to encode and decode. 
 * How to use:
 * <pre>
 * {@code
 * // get the AES key
 * String aseKey = generateAESKey(128);
 * // the content string you want to encrypt using AES key
 * String text = "404 Team No.1";
 * // encrypt
 * String encryptedContent = encryptHex(text, aseKey);
 * // decrypt
 * String decryptedContent = decryptHex(encryptedContent, aseKey);
 * }
 * </pre>
 * Part of codes refers from
 * 	https://blog.csdn.net/jellyjiao2008/article/details/80475446
 * 	https://segmentfault.com/a/1190000015943620
 * @author yuqiangz@student.unimelb.edu.au
 *
 */
public class AES {
    private static final String ENCRY_ALGORITHM = "AES";
    private static final String CIPHER_MODE = "AES/ECB/PKCS5Padding";
    private static final String CHARACTER = "UTF-8";
    private static final int PWD_SIZE = 16;
    /*
	public static void main(String[] args) throws Exception {
		String aseKey = generateAESKey(128);
		//String aseKeyString = new String(Base64.getEncoder().encode(aseKey), "UTF-8");
		System.out.println(aseKey);
	
		String text = "To be continue";
		String encodedContent = encryptHex(text, aseKey);
		String decodedContent = decryptHex(encodedContent, aseKey);
		System.out.println(decodedContent);
		
	}
	*/

	/**
	 * Generate AES Key.
	 * @param length the length of generated AES key
	 */
	public static String generateAESKey(int length) throws Exception {
		KeyGenerator kgen = null;
		kgen = KeyGenerator.getInstance(ENCRY_ALGORITHM);
		kgen.init(length);
		SecretKey skey = kgen.generateKey();
		String ret = new String(Base64.getEncoder().encode(skey.getEncoded()), CHARACTER);
		return ret;
	}
	
    /**
     * handle the key string, append 0 if the length is not enough.
     */
    private static byte[] pwdHandler(String password) throws UnsupportedEncodingException {
        byte[] data = null;
        if (password == null) {
            password = "";
        }
        StringBuffer sb = new StringBuffer(PWD_SIZE);
        sb.append(password);
        while (sb.length() < PWD_SIZE) {
            sb.append("0");
        }
        if (sb.length() > PWD_SIZE) {
            sb.setLength(PWD_SIZE);
        }

        data = sb.toString().getBytes("UTF-8");

        return data;
    }

    /**
     * encrypt the bytes
     */
    public static byte[] encrypt(byte[] clearTextBytes, byte[] pwdBytes) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(pwdBytes, ENCRY_ALGORITHM);
            Cipher cipher = Cipher.getInstance(CIPHER_MODE);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] cipherTextBytes = cipher.doFinal(clearTextBytes);
            return cipherTextBytes;

        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * decrypt the encrypted bytes
     */
    public static byte[] decrypt(byte[] cipherTextBytes, byte[] pwdBytes) {

        try {
            SecretKeySpec keySpec = new SecretKeySpec(pwdBytes, ENCRY_ALGORITHM);
            Cipher cipher = Cipher.getInstance(CIPHER_MODE);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] clearTextBytes = cipher.doFinal(cipherTextBytes);
            return clearTextBytes;

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    //======================>BASE64<======================

    /**
     * BASE64 encrypt
     */
    public static String encryptBase64(String clearText, String password) {
        try {
            byte[] cipherTextBytes = encrypt(clearText.getBytes(CHARACTER), pwdHandler(password));
            String cipherText = new String(Base64.getEncoder().encode(cipherTextBytes));
            return cipherText;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * BASE64 decrypt
     */
    public static String decryptBase64(String cipherText, String password) {
        try {
        	byte[] cipherTextBytes = Base64.getDecoder().decode(cipherText);
            byte[] clearTextBytes = decrypt(cipherTextBytes, pwdHandler(password));
            return new String(clearTextBytes, CHARACTER);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    //======================>HEX<======================
    /**
     * HEX encrypt
     */
    public static String encryptHex(String clearText, String password) {
        try {
            byte[] cipherTextBytes = encrypt(clearText.getBytes(CHARACTER), pwdHandler(password));

            // convert to hex format
            String cipherText = byte2hex(cipherTextBytes);
            return cipherText;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * HEX decrypt
     */
    public static String decryptHex(String cipherText, String password) {
        try {
            byte[] cipherTextBytes = hex2byte(cipherText);
            byte[] clearTextBytes = decrypt(cipherTextBytes, pwdHandler(password));
            return new String(clearTextBytes, CHARACTER);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String byte2hex(byte[] bytes) {
        StringBuffer sb = new StringBuffer(bytes.length * 2);
        String tmp = "";
        for (int n = 0; n < bytes.length; n++) {
            tmp = (java.lang.Integer.toHexString(bytes[n] & 0XFF));
            if (tmp.length() == 1) {
                sb.append("0");
            }
            sb.append(tmp);
        }
        return sb.toString().toUpperCase();
    }

    /**
     * vert hex string to byte string
     */
    private static byte[] hex2byte(String str) {
        if (str == null || str.length() < 2) {
            return new byte[0];
        }
        str = str.toLowerCase();
        int l = str.length() / 2;
        byte[] result = new byte[l];
        for (int i = 0; i < l; ++i) {
            String tmp = str.substring(2 * i, 2 * i + 2);
            result[i] = (byte) (Integer.parseInt(tmp, 16) & 0xFF);
        }
        return result;
    }
}