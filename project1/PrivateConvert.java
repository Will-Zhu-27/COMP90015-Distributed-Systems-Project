package test2;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.PasswordFinder;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.util.io.pem.PemReader;

public class PrivateConvert{
	static String privateKeyString = "MIIEpQIBAAKCAQEA0FJs4yE8pQv4+ufrG8MR63iUGyngf1cJgUjcErWBVWKwk2fk3hSUJ7/kruaeqkV1J3yTwX6hh3BWOdZl3e3kTS/wzz8HUBQjvf2bgA32pNj92ZuetlS1maJtMxnrU2b0ROMihrFPXKlrN1U2pOCwS901a/22teX4TX3KcYzQUZZ/Fe93aZqjwW8vlwBxiz6w0ChKuIaW7ZG+08nX3e3afVKx/nmc5Yank1DVcVBKtFLqTJ4+36h8Ea0WgvG8iRnvnKuCN9pmdNZF4M4yR8TNOrM43NTSU+tZ+AgIXrLATtRvjSmagHr1GUNqYABwq74mMJUilrLoN6GK+XLIPbc4RwIDAQABAoIBACN4lW/LHf9gXYGjcaXlfiyg+F7zr3UfRdAMruREoYP/bN98hjzGNj/abb7WJ9gIQZ16mdINlMVx/EyW0/uI4SG/cvpm2gDpnPhhsVOJjSCejgK6e7jCDbXeMUfNNwOZeSgtoJhcoo29jzL5wHcshvgz7z+3EKBCSxguvgS9nE73yXt7GEZ19szcawt1xKfagEGpc3GCWLR11h00hTaBK7OL9suVQVtiwTTYQqgc8t4mOQi+HuFmCWzIl5Pe7DZtggMCrQOR/nTDwoDu4vP3AtcHprocfjvdLBvCJqctKgOdU3nBCKQ9kAWT+MTw6y8YpLGL4AAkiVYg1+NU9wPQEzECgYEA7HcMQIA0wVzsQxsnd/D/wxs7sCkjPK83uSIK0t00HR+85HCZ93MZBJTQDaj/hKMDsgWDsiERdWyMnXRSr8680KSfbq6E4EV3pqLEEz28EhzsKZDz+ww0p3fWZNBH4Cb0OwmPQDSrHOyg627dOaiPDdWcsaccM503JCXpGawYPVkCgYEA4Ygvf9QsBVYB83APY4Hm1QSyfdDYDJHAzujzbi/z2EgwU5YtWWfH8TuJ5kM+PNrA3a8dfrFPFi4nXq/oqnaOY2+xOpl8BM15kltB8Ip7dsYV2FucYG/Z73rXuSLraQzvY9za7EOj4hrJZWM1PmYtytU7zoMoGGeeryH6FeC4Tp8CgYEAolF6BJC9JfqeZ9Ys+qVhO9Hm4B7tBEwWySu4GFUl39QYewtcdUL56m4ofygB9k9cSwiEBXOzo1JXGAJwfCRC8kn+8yAzMCwfXTCfvcGD5z8ZUdMh17PiNQ8LCXr7y7+RqTD/t4gv6ZP5RoN8soalZE682CopLrj9z/+CClBa90kCgYEAg59fWVMSfeq8KL71vDVL6nZbVWJVNMC7rgX2TBBgV5GJ4r56qPsQjZEZ4fDMmedxN+/DXvVMGr3E7FXti861OwMsg+6fmo3wraHk0eWAOMlSZnrQwNeGcWVYEQx4J08NR1LV0Z2IP6UydKF5qXkosH/R76xL8jJHQh6qPapw5jECgYEAuej8XyAmsQW3rxJMmfTDG9ghzRS1aI+L3Gp+HvzdtQdEx9rbBs482rxCfRogWbQ9/IGUrpykzpihweGoYcTMvIQSajopxA9TdRmsbVXePUH/EsttJ7446KciHJ8JW3I81zdTDQ+GM/NNStPrO0Eo9Ss4W/U0I8sKkxd/mMITurU=";
		public static void main(String[] args) throws Exception {
			parseString2PrivateKey();
	}
	
	    public static RSAPrivateKey parseString2PrivateKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException{
	    	Security.addProvider(new BouncyCastleProvider());
	    	String password = "";

	    	// reads your key file
	    	PEMParser pemParser = new PEMParser(new FileReader("bitboxclient_rsa"));
	    	Object object = pemParser.readObject();
	    	JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");

	    	KeyPair kp;
	    	if (object instanceof PEMEncryptedKeyPair) {
	    		System.out.println("the private key part needs password!!!");
	    	    // Encrypted key - we will use provided password
	    	    PEMEncryptedKeyPair ckp = (PEMEncryptedKeyPair) object;
	    	    // uses the password to decrypt the key
	    	    PEMDecryptorProvider decProv = new JcePEMDecryptorProviderBuilder().build(password.toCharArray());
	    	    kp = converter.getKeyPair(ckp.decryptKeyPair(decProv));
	    	} else {
	    		System.out.println("the private key part does not need password!!!");
	    	    // Unencrypted key - no password needed
	    	    PEMKeyPair ukp = (PEMKeyPair) object;
	    	    kp = converter.getKeyPair(ukp);
	    	}

	    	// RSA
	    	KeyFactory keyFac = KeyFactory.getInstance("RSA");
	    	PrivateKey prik = kp.getPrivate();
	    	KeySpec keySpec = new PKCS8EncodedKeySpec(prik.getEncoded());
	    	return (RSAPrivateKey) keyFac.generatePrivate(keySpec);
	    }
}