package unimelb.bitbox;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Base64;

import unimelb.bitbox.PeerConnection.CONNECTION_STATUS;
import unimelb.bitbox.util.AES;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.SshWithRSA;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;
/**
 * Include all bitbox command and command handler
 * @author yuqiangz@student.unimelb.edu.au
 *
 */
public class Command {
	
	public static void authResponseHandler(ClientConnection connection, Document authResponseDoc) {
		boolean status = authResponseDoc.getBoolean("status");
		// Peer does not find this client
		if (status == false) {
			System.exit(0);
		}
		
		String encodedContentString = authResponseDoc.getString("AES128");
		//connection.log.info("encodedContentString is:" + encodedContentString);
		byte[] encodedContent = Base64.getDecoder().decode(encodedContentString);
		// use private key to decrypt
		try {
			RSAPrivateKey privateKey = SshWithRSA.parseString2PrivateKey();
			// get secret key
			connection.secretKey = new String(SshWithRSA.decrypt(encodedContent, privateKey), "utf-8");
			connection.sendClientRequest();
			// log.info("Get the secret key:" + secretKey);
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeySpecException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void authRequest(ClientConnection connection) {
		Document doc = new Document();
		doc.append("command", "AUTH_REQUEST");
		doc.append("identity", connection.client.getIdentity());
		connection.sendMessage(doc);
	}
	
	/**
	 * for PeerControlConnection class use
	 * @param connection
	 * @param doc
	 */
	public static void payloadHandler(PeerControlConnection connection, Document doc) {
		String encodedContentJsonString = doc.getString("payload");
		if (encodedContentJsonString == null) {
			connection.log.info("Error!!!"); // need more!!!!!
			return;
		}
		String decodedContentJsonString = new String(Base64.getDecoder().decode(encodedContentJsonString.getBytes()));
		String decryptedContentJsonString = AES.decryptHex(decodedContentJsonString, connection.secretKey);
		// connection.log.info("Received content from client:" + decryptedContentJsonString);
		// check client command and respond correspondingly
		Document decryptedDoc = Document.parse(decryptedContentJsonString);
		switch(decryptedDoc.getString("command")) {
			case "LIST_PEERS_REQUEST":{
				listPeersResponse(connection);
				break;
			}
			case "CONNECT_PEER_REQUEST":{
				connectPeerResponse(connection, decryptedDoc);
				break;
			}
			case "DISCONNECT_PEER_REQUEST":{
				disconnectPeerResponse(connection, decryptedDoc);
				break;
			}
		}
	}
	
	public static void authRequestHandler(PeerControlConnection connection, Document doc) {
		String requestedIdentity = doc.getString("identity");
		// get the public key string without prefix 
		String publicKeyString = connection.getPublicKey(requestedIdentity);
		// no corresponding public key
		if (publicKeyString == null) {
			authResponseFalse(connection);
		} else {
			try {
				authResponseTrue(connection, publicKeyString);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	private static void authResponseFalse(PeerControlConnection connection) {
		Document doc = new Document();
		doc.append("command", "AUTH_RESPONSE");
		doc.append("status", false);
		doc.append("message", "public key not found");
		connection.sendMessage(doc);
	}
	
	private static void authResponseTrue(PeerControlConnection connection, String publicKeyString) throws Exception {
		Document doc = new Document();
		doc.append("command", "AUTH_RESPONSE");
		// generate a secret key
		connection.secretKey = AES.generateAESKey(128);
		
		// convert the public key string into RSAPublicKey object.
		RSAPublicKey publicKey = SshWithRSA.decodePublicKey(Base64.getDecoder().decode(publicKeyString));
		// encrypt the secret key using public key
		byte[] encryptedContent = SshWithRSA.encrypt(connection.secretKey.getBytes(), publicKey);
		String encryptedContentString = Base64.getEncoder().encodeToString(encryptedContent);
		//log.info("encodedContentString is:" + encodedContentString);
		doc.append("AES128", encryptedContentString);
		doc.append("status", true);
		doc.append("message", "public key found");
		connection.sendMessage(doc);
	}
	
	private static void disconnectPeerResponse(PeerControlConnection connection, Document doc) {
		Document unencryptedDoc = new Document();
		unencryptedDoc.append("command", "DISCONNECT_PEER_REQUEST");
		String givenHost = doc.getString("host");
		String temp = "" + doc.get("port");
		int givenPort = Integer.parseInt(temp);
		unencryptedDoc.append("host", givenHost);
		unencryptedDoc.append("port", givenPort);
		if (connection.controlServer.serverMain.connectedPeerListContains(givenHost + ":" + givenPort) == false) {
			unencryptedDoc.append("status", false);
			unencryptedDoc.append("message", "connection not active");
		} else {
			if (connection.controlServer.serverMain.disconnectPeer(givenHost, givenPort) == true) {
				unencryptedDoc.append("status", true);
				unencryptedDoc.append("message", "disconnected from peer");
			} else {
				unencryptedDoc.append("status", false);
				unencryptedDoc.append("message", "connection not active");
			}
		}
		payload(connection, unencryptedDoc.toJson());
	}
	
	private static void connectPeerResponse(PeerControlConnection connection, Document doc) {
		Document unencryptedDoc = new Document();
		unencryptedDoc.append("command", "CONNECT_PEER_RESPONSE");
		String givenHost = doc.getString("host");
		String temp = "" + doc.get("port");
		int givenPort = Integer.parseInt(temp);
		unencryptedDoc.append("host", givenHost);
		unencryptedDoc.append("port", givenPort);
		// already connect to given peer
		if(connection.controlServer.serverMain.connectedPeerListContains(givenHost + ":" + givenPort) == true) {
			unencryptedDoc.append("status", true);
			unencryptedDoc.append("message", "already connected to peer"); // need edit after debug
		} 
		// try to connect
		else {
			try {
				PeerConnection givenPeerConnection = connection.controlServer.serverMain.connectGivenPeer(givenHost,
						givenPort);
				while (givenPeerConnection.connectionStatus == CONNECTION_STATUS.WAITING);
				if (givenPeerConnection.connectionStatus == CONNECTION_STATUS.ONLINE) {
					unencryptedDoc.append("status", true);
					unencryptedDoc.append("message", "connected to peer");
				} else if (givenPeerConnection.connectionStatus == CONNECTION_STATUS.OFFLINE) {
					unencryptedDoc.append("status", false);
					unencryptedDoc.append("message", "connection failed");
				} else if (givenPeerConnection.connectionStatus == CONNECTION_STATUS.WAITING) {
					unencryptedDoc.append("status", false);
					unencryptedDoc.append("message", "connection in waiting");
				}
			} catch (Exception e) {
				unencryptedDoc.append("status", false);
				unencryptedDoc.append("message", "connection failed");
			}
		}
		payload(connection, unencryptedDoc.toJson());
	}
	
	private static void listPeersResponse(PeerControlConnection connection) {
		ArrayList<Document> peerDocList = connection.getConnectedPeerDocumentArrayList();
		Document doc = new Document();
		doc.append("command", "LIST_PEERS_RESPONSE");
		doc.append("peers", peerDocList);
		payload(connection, doc.toJson());
	}
	
	/**
	 * send encrypted message to client
	 * @param message unencrypted String you want to send to client
	 */
	private static void payload(PeerControlConnection connection, String message) {
		// connection.log.info("The content before encrypted:" + message);
		Document doc = new Document();
		String encryptedContent =  AES.encryptHex(message, connection.secretKey);
		String encodedContent = Base64.getEncoder().encodeToString(encryptedContent.getBytes());
		doc.append("payload", encodedContent);
		connection.sendMessage(doc);
	}
	
	/**
	 * for ClientConnection class use
	 * @param connection
	 * @param doc
	 */
	public static void payloadHandler(ClientConnection connection, Document doc) {
		String encodedContentJsonString = doc.getString("payload");
		if (encodedContentJsonString == null) {
			connection.log.info("Error!!!"); // need more!!!!!
			return;
		}
		String decodedContentJsonString = new String(Base64.getDecoder().decode(encodedContentJsonString.getBytes()));
		String decryptedContentJsonString = AES.decryptHex(decodedContentJsonString, connection.secretKey);
		connection.log.info("The result: " + decryptedContentJsonString);
	}
	
	public static void handshakeRequestHandler(PeerConnection connection, Document handshakeRequestDoc) throws IOException {
		connection.server.checkConnectedPorts();
		Document hostPort = (Document) handshakeRequestDoc.get("hostPort");
		// System.out.println(hostPort.toJson());
		connection.connectedHost = hostPort.getString("host");
		String temp = "" + hostPort.get("port");
		connection.connectedPort = Integer.parseInt(temp);
		// for second connect
		connection.server.checkConnectedPorts();
		if (connection.server.connectedPeerListContains(connection.connectedHost + ":" + connection.connectedPort)) {
			connection.connectionStatus = CONNECTION_STATUS.OFFLINE;
			if (ServerMain.communicationMode.equals(ServerMain.UDP_MODE)) {
				connection.server.waitingPeerList.remove(connection.connectedHost + ":" + connection.connectedPort);
			}
			invalidProtocol(connection);
		} else if (ServerMain.currentIncomingconnectionNum >= ServerMain.maximunIncommingConnections) {
			connection.connectionStatus = CONNECTION_STATUS.OFFLINE;
			if (ServerMain.communicationMode.equals(ServerMain.UDP_MODE)) {
				connection.server.waitingPeerList.remove(connection.connectedHost + ":" + connection.connectedPort);
			}
			connectionRefused(connection);
		} else {
			connection.connectionStatus = CONNECTION_STATUS.ONLINE;
			handshakeResponse(connection);
		}
	}
	
	public static void handshakeResponseHandler(PeerConnection connection, Document handshakeResponseDoc) {
		Document hostPort = (Document) handshakeResponseDoc.get("hostPort");
		// System.out.println(hostPort.toJson());
		connection.connectedHost = hostPort.getString("host");
		String temp = "" + hostPort.get("port");
		connection.connectedPort = Integer.parseInt(temp);
		// mark as successful connection
		if (connection.server.connectedPeerListPut(connection.connectedHost + ":" + connection.connectedPort, connection) == false) {
			connection.connectionStatus = CONNECTION_STATUS.OFFLINE;
			if (ServerMain.communicationMode.equals(ServerMain.UDP_MODE)) {
				connection.server.waitingPeerList.remove(connection.connectedHost + ":" + connection.connectedPort);
			}
			if (ServerMain.communicationMode.equals(ServerMain.TCP_MODE)) {
				try {
					connection.getConnectedSocket().close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} else {
			connection.connectionStatus = CONNECTION_STATUS.ONLINE;
			connection.log.info("*** the connection is in online status ***");
		}
		// sync at the beginning of a successful connection
		for (FileSystemEvent pathEvent : ServerMain.fileSystemManager.generateSyncEvents()) {
			// log.info(pathEvent.toString());
			connection.server.processFileSystemEvent(pathEvent);
		}
	}
	
	public static void connectionRefusedHandler(PeerConnection connection) {
		connection.connectionStatus = CONNECTION_STATUS.OFFLINE;
		if (ServerMain.communicationMode.equals(ServerMain.UDP_MODE)) {
			connection.server.waitingPeerList.remove(connection.connectedHost + ":" + connection.connectedPort);
			connection.server.connectedPeerListRemove(connection.connectedHost + ":" + connection.connectedPort);
		}
		if (ServerMain.communicationMode.equals(ServerMain.TCP_MODE)) {
			try {
				connection.getConnectedSocket().close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public static void invalidProtocolHandler(PeerConnection connection) {
		connection.server.connectedPeerListRemove(connection.connectedHost + ":" + connection.connectedPort);
		connection.connectionStatus = CONNECTION_STATUS.OFFLINE;
		if (ServerMain.communicationMode.equals(ServerMain.UDP_MODE)) {
			connection.server.waitingPeerList.remove(connection.connectedHost + ":" + connection.connectedPort);
		}
		if (ServerMain.communicationMode.equals(ServerMain.TCP_MODE)) {
			try {
				connection.getConnectedSocket().close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public static void fileCreateRequestHandler(PeerConnection connection, Document fileCreateRequestDoc) {
		fileCreateResponse(connection, fileCreateRequestDoc);
	}
	
	public static void fileBytesRequestHandler(PeerConnection connection, Document fileBytesRequestDoc) {
		try {
			fileBytesResponse(connection, fileBytesRequestDoc);
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void fileBytesResponseHandler(PeerConnection connection, Document fileBytesResponseDoc) {
		String pathName = fileBytesResponseDoc.getString("pathName");
		long position = fileBytesResponseDoc.getLong("position");
		String content = fileBytesResponseDoc.getString("content");
		ByteBuffer byteBuffer = ByteBuffer.wrap(Base64.getDecoder().decode(content));	
		try {
			ServerMain.fileSystemManager.writeFile(pathName, byteBuffer, position);
			if (!ServerMain.fileSystemManager.checkWriteComplete(pathName)) {
				fileBytesRequest(connection, fileBytesResponseDoc);
			}
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void fileDeleteRequestHandler(PeerConnection connection, Document fileDeleteRequestDoc) {
		fileDeleteResponse(connection, fileDeleteRequestDoc);
	}
	
	public static void fileModifyRequestHandler(PeerConnection connection, Document fileModifyRequestDoc) {
		try {
			fileModifyResponse(connection, fileModifyRequestDoc);
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void directoryCreateRequestHandler(PeerConnection connection, Document directoryCreateRequestDoc) {
		directoryCreateResponse(connection, directoryCreateRequestDoc);
	}
	
	public static void directoryDeleteRequestHandler(PeerConnection connection, Document directoryDeleteRequestDoc) {
		directoryDeleteResponse(connection, directoryDeleteRequestDoc);
	}
	
	/**
	 * @author yuqiangz@student.unimelb.edu.au
	 * 
	 * @param message the content of FILE_CREATE_REQUEST
	 */
	public static void fileCreateResponse(PeerConnection connection, Document message) {
		String pathName = message.getString("pathName");
		System.out.println(pathName);
		Document fileDescriptor = (Document) message.get("fileDescriptor");
		String md5 = fileDescriptor.getString("md5");
		long length = fileDescriptor.getLong("fileSize");
		long lastModified = fileDescriptor.getLong("lastModified");

		Document doc = new Document();
		doc.append("command", "FILE_CREATE_RESPONSE");
		doc.append("fileDescriptor", fileDescriptor);
		doc.append("pathName", pathName);
		//log.info("pathName is " + pathName);
		if (!ServerMain.fileSystemManager.isSafePathName(pathName)) {
			doc.append("message", "unsafe pathname given");
			doc.append("status", false);
			connection.sendMessage(doc);
			return;
		}
		//log.info(pathName + " is safe path name");
		if (ServerMain.fileSystemManager.fileNameExists(pathName)) {
			doc.append("message", "pathname already exists");
			doc.append("status", false);
			connection.sendMessage(doc);
			return;
		}
		//log.info(pathName + " doesn't exist bebore.");
		try {
			if (ServerMain.fileSystemManager.createFileLoader(pathName, md5, 
				length, lastModified)) {
				//connection.log.info("create file loader successfully!");
				try {
					if (ServerMain.fileSystemManager.checkShortcut(pathName)) {
						doc.append("message", "use a local copy");
						doc.append("status", true);
						connection.sendMessage(doc);
					} else {
						doc.append("message", "file loader ready");
						doc.append("status", true);
						connection.sendMessage(doc);
						fileBytesRequest(connection, message);
					}
				} catch (NoSuchAlgorithmException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return;
			} else {
				doc.append("message", "there was a problem creating the file");
				doc.append("status", false);
				connection.sendMessage(doc);
				return;
			}
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * @author yuqiangz@student.unimelb.edu.au
	 * 
	 * @param message the content of FILE_CREATE_REQUEST or FILE_BYTES_RESPONSE
	 *                which doesn't complete write.
	 */
	public static void fileBytesRequest(PeerConnection connection, Document message) {
		Document fileDescriptor = (Document) message.get("fileDescriptor");
		Document doc = new Document();
		String receivedCommand = message.getString("command");
		doc.append("command", "FILE_BYTES_REQUEST");
		doc.append("fileDescriptor", fileDescriptor);
		doc.append("pathName", message.getString("pathName"));
		long fileSize = fileDescriptor.getLong("fileSize");
		if (receivedCommand.equals("FILE_CREATE_REQUEST") || 
			receivedCommand.equals("FILE_MODIFY_REQUEST")) {
			doc.append("position", 0);
			doc.append("length", connection.getReadFileLength(doc, 0, fileSize));
		} else if (receivedCommand.equals("FILE_BYTES_RESPONSE")) {
			long startPos = message.getLong("position") + 
				message.getLong("length");
			doc.append("position", startPos);
			doc.append("length", connection.getReadFileLength(doc, startPos, fileSize));
		}
		connection.sendMessage(doc);
	}
	
	/**
	 * @author yuqiangz@student.unimelb.edu.au
	 */
	public static void invalidProtocol(PeerConnection connection) {
		Document doc = new Document();
		doc.append("command", "INVALID_PROTOCOL");
		doc.append("message", "message must a command field as string");
		connection.sendMessage(doc);
		try {
			connection.server.connectedPeerListRemove(connection.connectedHost + ":" + connection.connectedPort);
			if (ServerMain.communicationMode.equals(ServerMain.TCP_MODE)) {
				connection.getConnectedSocket().close();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		connection.server.connectedPeerListRemove(connection.connectedHost + ":" + connection.connectedPort);
		connection.connectionStatus = CONNECTION_STATUS.OFFLINE;
		if (ServerMain.communicationMode.equals(ServerMain.UDP_MODE)) {
			connection.server.waitingPeerList.remove(connection.connectedHost + ":" + connection.connectedPort);
		}
	}
	
	/**
	 * @author yuqiangz@student.unimelb.edu.au
	 */
	public static void connectionRefused(PeerConnection connection) throws IOException {
		Document doc = new Document();
		doc.append("command", "CONNECTION_REFUSED");
		doc.append("message", "connection limit reached");
		ArrayList<Document> peerDocList = connection.getConnectedPeerDocumentArrayList();
		doc.append("peers", peerDocList);
		connection.sendMessage(doc);
		connection.connectionStatus = CONNECTION_STATUS.OFFLINE;
		if (ServerMain.communicationMode.equals(ServerMain.UDP_MODE)) {
			connection.server.waitingPeerList.remove(connection.connectedHost + ":" + connection.connectedPort);
		}
		if (ServerMain.communicationMode.equals(ServerMain.TCP_MODE)) {
			connection.getConnectedSocket().close();
		}
	}
	
	/**
	 * @author yuqiangz@student.unimelb.edu.au
	 */
	public static void handshakeResponse(PeerConnection connection) throws IOException {
		Document doc = new Document();
		doc.append("command", "HANDSHAKE_RESPONSE");
		doc.append("hostPort", new HostPort(connection.host, connection.port).toDoc());
		connection.sendMessage(doc);	
		
		// mark as a successful connection
		if(connection.server.connectedPeerListPut(connection.connectedHost + ":" + connection.connectedPort, 
				connection) == false) {
			if (ServerMain.communicationMode.equals(ServerMain.TCP_MODE)) {
				connection.getConnectedSocket().close();
			}
		}
		
		// sync at the beginning of a successful connection
		for(FileSystemEvent pathevent : 
			ServerMain.fileSystemManager.generateSyncEvents()) {
			//log.info(pathevent.toString());
			connection.server.processFileSystemEvent(pathevent);
		}
	}
	
	/**
	 * @author yuqiangz@student.unimelb.edu.au
	 * 
	 * @param message the content of FILE_BYTES_REQUEST
	 */
	public static void fileBytesResponse(PeerConnection connection, Document message) 
		throws NoSuchAlgorithmException, IOException {
		Document doc = new Document();
		Document fileDescriptor = (Document) message.get("fileDescriptor");

		doc.append("command", "FILE_BYTES_RESPONSE");
		doc.append("fileDescriptor", fileDescriptor);
		doc.append("pathName", message.getString("pathName"));
		doc.append("position", message.getLong("position"));
		doc.append("length", message.getLong("length"));

		long startPos = message.getLong("position");
		long length = message.getLong("length");
		String md5 = fileDescriptor.getString("md5");
		ByteBuffer byteBuffer = 
			ServerMain.fileSystemManager.readFile(md5, startPos, length);
		String encodedString = 
			Base64.getEncoder().encodeToString(byteBuffer.array());
		doc.append("content", encodedString);
		if (byteBuffer.array() == null) {
			doc.append("message", "unsuccessful read");
			doc.append("status", false);
		}

		doc.append("message", "successful read");
		doc.append("status", true);
		if (ServerMain.communicationMode.equals(ServerMain.UDP_MODE)) {
			fileBytesResponseUDPHandler(connection, doc);
		} else if (ServerMain.communicationMode.equals(ServerMain.TCP_MODE)) {
			connection.sendMessage(doc);
		}	
	}
	
	/**
	 * make sure the length of FILE_BYTES_RESPONSE within UDP limit.
	 * If doc is too big, the content will be reduced until within
	 * UDP limit; 
	 */
	private static void fileBytesResponseUDPHandler(PeerConnection connection, Document originalDoc) throws NoSuchAlgorithmException, IOException {
		if (originalDoc.toJson().getBytes().length > connection.blockSize - 1) {
			long originalLength = originalDoc.getLong("length");
			long newLength = originalLength * 8 / 10;
			fileBytesResponse(connection, originalDoc, newLength);
		} else {
			connection.sendMessage(originalDoc);
		}
	}
	
	/**
	 * This method is only called by fileBytesResponseUDP.
	 */
	private static void fileBytesResponse(PeerConnection connection, Document message, long newLength) throws NoSuchAlgorithmException, IOException {
		Document doc = new Document();
		Document fileDescriptor = (Document) message.get("fileDescriptor");
		long startPos = message.getLong("position");
		doc.append("command", "FILE_BYTES_RESPONSE");
		doc.append("fileDescriptor", fileDescriptor);
		doc.append("pathName", message.getString("pathName"));
		doc.append("position", startPos);
		doc.append("length", newLength);
		String md5 = fileDescriptor.getString("md5");
		ByteBuffer byteBuffer = ServerMain.fileSystemManager.readFile(md5, startPos, newLength);
		String encodedString = Base64.getEncoder().encodeToString(byteBuffer.array());
		doc.append("content", encodedString);
		if (byteBuffer.array() == null) {
			doc.append("message", "unsuccessful read");
			doc.append("status", false);
		}

		doc.append("message", "successful read");
		doc.append("status", true);
		fileBytesResponseUDPHandler(connection, doc);
	}
	
	/**
	 * @author laif1
	 * 
	 * @param message the content of FILE_DELETE_REQUEST
	 */
	public static void fileDeleteResponse(PeerConnection connection, Document message) {
		String pathName = message.getString("pathName");
		System.out.println(pathName);
		Document fileDescriptor = (Document) message.get("fileDescriptor");
		String md5 = fileDescriptor.getString("md5");
		long lastModified = fileDescriptor.getLong("lastModified");

		Document doc = new Document();
		doc.append("command", "FILE_DELETE_RESPONSE");
		doc.append("fileDescriptor", fileDescriptor);
		doc.append("pathName", message.getString("pathName"));

		if (!ServerMain.fileSystemManager.isSafePathName(pathName)) {
			doc.append("message", "unsafe pathname given");
			doc.append("status", false);
			connection.sendMessage(doc);
			return;
		}

		if (!ServerMain.fileSystemManager.fileNameExists(pathName)) {
			doc.append("message", "pathname does not exist");
			doc.append("status", false);
			connection.sendMessage(doc);
			return;
		}

		boolean deleteStatus = ServerMain.fileSystemManager.deleteFile(pathName, 
			lastModified, md5);
		if (deleteStatus) {
			doc.append("message", "File Deleted");
			doc.append("status", true);
		} else {
			doc.append("message", "there was a problem deleting the file");
			doc.append("status", false);
		}
		connection.sendMessage(doc);
	}
	
	/**
	 * @author laif1
	 * 
	 * @param message the content of FILE_DELETE_REQUEST
	 */
	public static void fileModifyResponse(PeerConnection connection, Document message) 
		throws IOException, NoSuchAlgorithmException {
		String pathName = message.getString("pathName");
		System.out.println(pathName);
		Document fileDescriptor = (Document) message.get("fileDescriptor");
		String md5 = fileDescriptor.getString("md5");
		long lastModified = fileDescriptor.getLong("lastModified");

		boolean modifyLoaderStatus = 
			ServerMain.fileSystemManager.modifyFileLoader(pathName, md5, lastModified);

		Document doc = new Document();
		doc.append("command", "FILE_MODIFY_RESPONSE");
		doc.append("fileDescriptor", fileDescriptor);
		doc.append("pathName", pathName);

		if (!ServerMain.fileSystemManager.isSafePathName(pathName)) {
			doc.append("message", "unsafe pathname given");
			doc.append("status", false);
			connection.sendMessage(doc);
			return;
		}

		if (!ServerMain.fileSystemManager.fileNameExists(pathName)) {
			doc.append("message", "pathname does not exist");
			doc.append("status", false);
			connection.sendMessage(doc);
			return;
		}

		if (modifyLoaderStatus) {
			try {
				if (ServerMain.fileSystemManager.checkShortcut(pathName)) {
					doc.append("message", 
						"file already exists with matching content");
					doc.append("status", true);
					connection.sendMessage(doc);
				} else {
					System.out.print("It works");
					doc.append("message", "file loader ready");
					doc.append("status", true);
					connection.sendMessage(doc);
					System.out.print("It works modify");
					fileBytesRequest(connection, message); // need to test
				}
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return;
		} else {
			doc.append("message", "there was a problem modifying the file");
			doc.append("status", false);
			connection.sendMessage(doc);
			return;
		}
	}
	
	/**
	 * @author laif1
	 */
	public static void directoryCreateResponse(PeerConnection connection, Document message) {
		String pathName = message.getString("pathName");

		Document doc = new Document();
		doc.append("command", "DIRECTORY_CREATE_RESPONSE");
		doc.append("pathName", message.getString("pathName"));
		
		if (!ServerMain.fileSystemManager.isSafePathName(pathName)) {
			doc.append("message", "unsafe pathname given");
			doc.append("status", false);
			connection.sendMessage(doc);
			return;
		}

		if (ServerMain.fileSystemManager.fileNameExists(pathName)) {
			doc.append("message", "pathname already exists");
			doc.append("status", false);
			connection.sendMessage(doc);
			return;
		}
		
		boolean directoryCreateStatus = 
			ServerMain.fileSystemManager.makeDirectory(pathName);
		if (directoryCreateStatus) {
			doc.append("message", "directory created");
		} else {
			doc.append("message", "there was a problem creating the directory");
		}
		doc.append("status", directoryCreateStatus);
		connection.sendMessage(doc);
	}
	
	/**
	 * @author laif1
	 * 
	 * @param message the content of DIRECTORY_DELETE_REQUEST
	 */
	public static void directoryDeleteResponse(PeerConnection connection, Document message) {
		String pathName = message.getString("pathName");
		
		Document doc = new Document();
		doc.append("command", "DIRECTORY_DELETE_RESPONSE");
		doc.append("pathName", message.getString("pathName"));
		
		if (!ServerMain.fileSystemManager.isSafePathName(pathName)) {
			doc.append("message", "unsafe pathname given");
			doc.append("status", false);
			connection.sendMessage(doc);
			return;
		}

		if (!ServerMain.fileSystemManager.dirNameExists(pathName)) {
			doc.append("message", "directory name does not exist");
			doc.append("status", false);
			connection.sendMessage(doc);
			return;
		}
		
		boolean directoryDeleteStatus = 
			ServerMain.fileSystemManager.deleteDirectory(pathName);
		if (directoryDeleteStatus) {
			doc.append("message", "directory deleted");
		} else {
			doc.append("message", "there was a problem deleting the directory");
		}
		doc.append("status", directoryDeleteStatus);
		connection.sendMessage(doc);
	}
	
	/**
	 * @author yuqiangz@student.unimelb.edu.au
	 */
	public static void handshakeRequest(PeerConnection connection) {
		Document doc = new Document();
		doc.append("command", "HANDSHAKE_REQUEST");
		doc.append("hostPort", new HostPort(connection.host, connection.port).toDoc());
		connection.sendMessage(doc);
	}
}