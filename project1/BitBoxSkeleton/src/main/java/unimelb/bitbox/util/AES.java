package unimelb.bitbox.util;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Select AES/ECB/PKCS5Padding to encode and decode. Part of codes refers from
 * https://blog.csdn.net/jellyjiao2008/article/details/80475446
 * 
 * @author yuqiangz@student.unimelb.edu.au
 *
 */
public class AES {
	public static final String ALGORITHM = "AES/ECB/PKCS5Padding";
	/*
	public static void main(String[] args) throws Exception {
		byte[] aseKey = generateDesKey(128);
		String text = "To be continue";
		byte[] encodedText = AesEncode(text, aseKey);
		System.out.println("Encoded Text is :" + new String(encodedText));
		byte[] decodedText = AesDecode(encodedText, aseKey);
		System.out.println("Decoded Text is :" + new String(decodedText));
	}*/

	/**
	 * Generate AES Key.
	 * @param length the length of generated AES key
	 */
	public static byte[] generateAESKey(int length) throws Exception {
		KeyGenerator kgen = null;
		kgen = KeyGenerator.getInstance("AES", "BC");
		kgen.init(length);
		SecretKey skey = kgen.generateKey();
		return skey.getEncoded();
	}

	/**
	 * Use provided AES key to encode String.
	 * @param content the string you want to encode.
	 * @param key the key used to encode.
	 * @return the byte format of encoded content.
	 */
	public static byte[] AESEncode(String content, byte[] key) throws Exception {
		byte[] result = null;
		Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
		Cipher cipher = Cipher.getInstance(ALGORITHM, "BC");
		SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
		cipher.init(Cipher.ENCRYPT_MODE, keySpec);
		result = cipher.doFinal(content.getBytes("UTF-8"));
		return result;
	}
	
	/**
	 * Use provided AES key to decode encoded content.
	 * @param encodedContent encoded content you want to decode
	 * @param key the key used to decode.
	 * @return the byte format of decoded content.
	 */
	public static byte[] AESDecode(byte[] encodedContent, byte[] key) 
		throws NoSuchAlgorithmException, NoSuchProviderException, 
		NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, 
		InvalidKeyException {
		byte[] decodedContent = null;
		SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        Cipher out = Cipher.getInstance(ALGORITHM, "BC");    
        out.init(Cipher.DECRYPT_MODE, keySpec);
        decodedContent = out.doFinal(encodedContent);  
        return decodedContent; 
	}
}