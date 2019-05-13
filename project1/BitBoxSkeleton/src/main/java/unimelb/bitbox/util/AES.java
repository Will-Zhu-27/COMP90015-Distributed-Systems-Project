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

public class AES {
	public static final String ALGORITHM = "AES/ECB/PKCS5Padding";
	public static void main(String[] args) throws Exception {
		byte[] aseKey = generateDesKey(128);
		String text = "To be continue";
		byte[] encodedText = AesEncode(text, aseKey);
		System.out.println("Encoded Text is :" + new String(encodedText));
		byte[] decodedText = AesDecode(encodedText, aseKey);
		System.out.println("Decoded Text is :" + new String(decodedText));
	}

	public static byte[] generateDesKey(int length) throws Exception {
		// 实例化
		KeyGenerator kgen = null;
		kgen = KeyGenerator.getInstance("AES");
		// 设置密钥长度
		kgen.init(length);
		// 生成密钥
		SecretKey skey = kgen.generateKey();
		// 返回密钥的二进制编码
		return skey.getEncoded();
	}

	public static byte[] AesEncode(String str, byte[] key) throws Exception {
		byte[] result = null;
		Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
		Cipher cipher = Cipher.getInstance(ALGORITHM, "BC");
		SecretKeySpec keySpec = new SecretKeySpec(key, "AES"); // 生成加密解密需要的Key
		cipher.init(Cipher.ENCRYPT_MODE, keySpec);
		result = cipher.doFinal(str.getBytes("UTF-8"));
		return result;
	}
	
	public static byte[] AesDecode(byte[] encodedContent, byte[] key) throws NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
		byte[] decodedContent = null;
		SecretKeySpec keySpec = new SecretKeySpec(key, "AES"); // 生成加密解密需要的Key
        Cipher out = Cipher.getInstance(ALGORITHM, "BC");    
        out.init(Cipher.DECRYPT_MODE, keySpec);
        decodedContent = out.doFinal(encodedContent);  
        return decodedContent; 
	}
}