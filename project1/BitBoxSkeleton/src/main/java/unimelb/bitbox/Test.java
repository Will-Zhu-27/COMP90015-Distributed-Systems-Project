package unimelb.bitbox;

import java.util.Base64;

import unimelb.bitbox.util.Document;

public class Test {
	public static void main(String[] args) {
		Document doc = new Document();
		long position = 0;
		doc.append("position", position);
		long length = 7790;
		doc.append("length", length);
		doc.append("content", "");
		System.out.println("未有content:" + doc.toJson().length() + ", " + doc.toJson().getBytes().length + "\n\n");
		
		String a = "*123%_[]@!!!!!!!dfdDWDWDWADA9l)";
		byte[] bytes = a.getBytes();
		System.out.println("Original content is:" + a);
		System.out.println("String length is " + a.length() + ", Bytes length is " + bytes.length);
		String encodedString = 
				Base64.getEncoder().encodeToString(bytes);
		int encodedLength = (int) (Math.ceil((double)(a.length())/3) * 4);
		System.out.println("Encoded content length should be " + encodedLength);
		System.out.println("Encoded content is:" + a);
		System.out.println("String length is " + encodedString.length() + ", Bytes length is " + encodedString.getBytes().length);
		
		long length2 = 12342123;
		Document doc2 = new Document();
		doc2.append("position", position);
		doc2.append("length", length);
		doc2.append("content", encodedString);
		System.out.println("有content:" + doc2.toJson().length() + ", " + doc2.toJson().getBytes().length + "\n\n");
	}
}