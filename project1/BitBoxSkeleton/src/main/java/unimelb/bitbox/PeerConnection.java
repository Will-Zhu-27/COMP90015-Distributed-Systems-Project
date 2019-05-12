package unimelb.bitbox;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Connection;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

/**
 * Deal with things about socket including sending and receiving message.
 */
public class PeerConnection extends Connection {
	private ServerMain server = null;
	private long blockSize;
	private String host;
	private int port;
	private String connectedHost;
	private int connectedPort;

	/**
	 * when peer receives a connection from other peer or client, use this 
	 * constructor to create an object of Class Connection to monitor.
	 */
	public PeerConnection(ServerMain server, Socket socket) throws IOException {
		super(socket);
		setCommonAttributesValue(server);
		start();
	}

	/**
	 * when peer makes a connection with other peer, use this constructor to 
	 * create an object of Class Connection to monitor.
	 */
	public PeerConnection(ServerMain server, Socket socket, String connectedHost, 
		int connectedPort) throws IOException {
		super(socket);
		setCommonAttributesValue(server);
		this.connectedHost = connectedHost;
		this.connectedPort = connectedPort;
		start();
	}

	/**
	 * Set common attributes value for constructor.
	 */
	private void setCommonAttributesValue(ServerMain server) 
		throws IOException {
		this.server = server;
		host = Configuration.getConfigurationValue("advertisedName");
		port = Integer.parseInt(Configuration.getConfigurationValue("port"));
		blockSize = 
			Long.parseLong(Configuration.getConfigurationValue("blockSize"));
	}

	public void run() {
		String data;
		try {
			while ((data = reader.readLine()) != null) {
				// convert message from string to JSON
				Document doc = Document.parse(data);
				checkCommand(doc);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			try {
				server.connectedPeerListRemove(connectedHost + ":" 
					+ connectedPort);
				connectedSocket.close();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
	}

	/**
	 * check command information and response.
	 * 
	 * @param doc received message.
	 */
	@Override
	public void checkCommand(Document doc) throws IOException {
		String command = doc.getString("command");
		/* receive AUTH_REQUEST from client */
		if (command.equals("AUTH_REQUEST")) {
			connectedPort = Integer.parseInt(Configuration.getConfigurationValue("clientPort"));
			connectedHost = "client";
		}
		/* receive HANDSHAKE_REQUEST */
		if (command.equals("HANDSHAKE_REQUEST")) {
			server.checkConnectedPorts();
			Document hostPort = (Document) doc.get("hostPort");
			// System.out.println(hostPort.toJson());
			connectedHost = hostPort.getString("host");
			String temp = "" + hostPort.get("port");
			connectedPort = Integer.parseInt(temp);
			if (server.connectedPeerListContains(connectedHost + ":" 
				+ connectedPort)) {
				invalidProtocol();
			} else if (ServerMain.currentIncomingconnectionNum >= 
				ServerMain.maximunIncommingConnections) {
				connectionRefused();
			} else {
				handshakeResponse();
			}
		}

		log.info("received " + command + " from " + connectedHost + ":" 
				+ connectedPort);

		/* receive HANDSHAKE_RESPONSE */
		if (command.equals("HANDSHAKE_RESPONSE")) {
			Document hostPort = (Document) doc.get("hostPort");
			// System.out.println(hostPort.toJson());
			connectedHost = hostPort.getString("host");
			String temp = "" + hostPort.get("port");
			connectedPort = Integer.parseInt(temp);
			// mark as successful connection
			if(server.connectedPeerListPut(connectedHost + ":" + connectedPort, 
				this) == false) {
				connectedSocket.close();
			}
			// sync at the beginning of a successful connection
			for(FileSystemEvent pathEvent : 
				ServerMain.fileSystemManager.generateSyncEvents()) {
				//log.info(pathEvent.toString());
				server.processFileSystemEvent(pathEvent);
			}
		}

		/* receive CONNECTION_REFUSED */
		if (command.equals("CONNECTION_REFUSED")) {
			connectedSocket.close();
		}

		/* receive INVALID_PROTOCOL */
		if (command.equals("INVALID_PROTOCOL")) {
			server.connectedPeerListRemove(connectedHost + ":" + connectedPort);
			connectedSocket.close();
		}

		/* receive FILE_CREATE_REQUEST */
		if (command.equals("FILE_CREATE_REQUEST")) {
			fileCreateResponse(doc);
		}

		/* receive FILE_BYTES_REQUEST */
		if (command.equals("FILE_BYTES_REQUEST")) {
			try {
				fileBytesResponse(doc);
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		/* receive FILE_BYTES_RESPONSE */
		if (command.equals("FILE_BYTES_RESPONSE")) {
			String pathName = doc.getString("pathName");
			long position = doc.getLong("position");
			String content = doc.getString("content");
			ByteBuffer byteBuffer = 
				ByteBuffer.wrap(Base64.getDecoder().decode(content));
			ServerMain.fileSystemManager.writeFile(pathName,byteBuffer,position);
			try {
				if(!ServerMain.fileSystemManager.checkWriteComplete(pathName)) {
					fileBytesRequest(doc);
				}
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		/* receive FILE_DELETE_REQUEST */
		if (command.equals("FILE_DELETE_REQUEST")) {
			fileDeleteResponse(doc);
		}

		/*
		 * receive FILE_DELETE_RESPONSE 
		 * do nothing
		 */

		/* receive FILE_MODIFY_REQUEST */
		if (command.equals("FILE_MODIFY_REQUEST")) {
			try {
				fileModifyResponse(doc);
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		/* receive DIRECTORY_CREATE_REQUEST */
		if (command.equals("DIRECTORY_CREATE_REQUEST")) {
			directoryCreateResponse(doc);
		}

		/* receive DIRECTORY_DELETE_REQUEST */
		if (command.equals("DIRECTORY_DELETE_REQUEST")) {
			directoryDeleteResponse(doc);
		}
	}

	/**
	 * @author yuqiangz@student.unimelb.edu.au
	 */
	public void handshakeResponse() throws IOException {
		Document doc = new Document();
		doc.append("command", "HANDSHAKE_RESPONSE");
		doc.append("hostPort", new HostPort(host, port).toDoc());
		sendMessage(doc);
		log.info("sending to " + connectedHost + ":" + connectedPort +
			doc.toJson());
		
		
		
		// mark as a successful connection
		if(server.connectedPeerListPut(connectedHost + ":" + connectedPort, 
			this) == false) {
			connectedSocket.close();
		}
		
		// sync at the beginning of a successful connection
		for(FileSystemEvent pathevent : 
			ServerMain.fileSystemManager.generateSyncEvents()) {
			//log.info(pathevent.toString());
			server.processFileSystemEvent(pathevent);
		}
	}

	/**
	 * @author yuqiangz@student.unimelb.edu.au
	 */
	public void handshakeRequest() {
		Document doc = new Document();
		doc.append("command", "HANDSHAKE_REQUEST");
		doc.append("hostPort", new HostPort(host, port).toDoc());
		sendMessage(doc);
		log.info("sending to " + connectedHost + ":" + connectedPort +
			doc.toJson());
	}

	/**
	 * @author yuqiangz@student.unimelb.edu.au
	 */
	public void connectionRefused() throws IOException {
		Document doc = new Document();
		doc.append("command", "CONNECTION_REFUSED");
		doc.append("message", "connection limit reached");
		ArrayList<Document> peerDocList = new ArrayList<Document>();
		HashMap<String, PeerConnection> connectedPeerList = 
			server.getConnectedPeerList();
		for (String peer : connectedPeerList.keySet()) {
			Document peerDoc = new Document();
			String host = (peer.split(":"))[0];
			int port = Integer.parseInt((peer.split(":"))[1]);
			peerDoc.append("host", host);
			peerDoc.append("port", port);
			peerDocList.add(peerDoc);
		}
		doc.append("peers", peerDocList);
		sendMessage(doc);
		log.info("sending to " + connectedHost + ":" + connectedPort +
			doc.toJson());
		connectedSocket.close();
	}

	/**
	 * @author yuqiangz@student.unimelb.edu.au
	 */
	public void invalidProtocol() {
		Document doc = new Document();
		doc.append("command", "INVALID_PROTOCOL");
		doc.append("message", "message must a command field as string");
		sendMessage(doc);
		log.info("sending to " + connectedHost + ":" + connectedPort + 
			doc.toJson());
		try {
			server.connectedPeerListRemove(connectedHost + ":" + connectedPort);
			connectedSocket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		server.connectedPeerListRemove(connectedHost + ":" + connectedPort);
	}

	/**
	 * @author yuqiangz@student.unimelb.edu.au
	 * 
	 * @param message the content of FILE_CREATE_REQUEST
	 */
	public void fileCreateResponse(Document message) {
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
			sendMessage(doc);
			log.info("sending to " + connectedHost + ":" + connectedPort + 
				doc.toJson());
			return;
		}
		//log.info(pathName + " is safe path name");
		if (ServerMain.fileSystemManager.fileNameExists(pathName)) {
			doc.append("message", "pathname already exists");
			doc.append("status", false);
			sendMessage(doc);
			log.info("sending to " + connectedHost + ":" + connectedPort + 
				doc.toJson());
			return;
		}
		//log.info(pathName + " doesn't exist bebore.");
		try {
			if (ServerMain.fileSystemManager.createFileLoader(pathName, md5, 
				length, lastModified)) {
				log.info("create file loader successfully!");
				try {
					if (ServerMain.fileSystemManager.checkShortcut(pathName)) {
						doc.append("message", "use a local copy");
						doc.append("status", true);
						sendMessage(doc);
					} else {
						doc.append("message", "file loader ready");
						doc.append("status", true);
						sendMessage(doc);
						fileBytesRequest(message);
					}
				} catch (NoSuchAlgorithmException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				log.info("sending to " + connectedHost + ":" + connectedPort + 
					doc.toJson());
				return;
			} else {
				doc.append("message", "there was a problem creating the file");
				doc.append("status", false);
				sendMessage(doc);
				log.info("sending to " + connectedHost + ":" + connectedPort + 
					doc.toJson());
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
	public void fileBytesRequest(Document message) {
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

			if (fileSize > blockSize) {
				doc.append("length", blockSize);
			} else {
				doc.append("length", fileSize);
			}
		}
		if (receivedCommand.equals("FILE_BYTES_RESPONSE")) {
			long startPos = message.getLong("position") + 
				message.getLong("length");
			doc.append("position", startPos);
			if (startPos + blockSize > fileSize) {
				doc.append("length", fileSize - startPos);
			} else {
				doc.append("length", blockSize);
			}
		}
		sendMessage(doc);
		log.info("sending to " + connectedHost + ":" + connectedPort + 
			doc.toJson());
	}

	/**
	 * @author yuqiangz@student.unimelb.edu.au
	 * 
	 * @param message the content of FILE_BYTES_REQUEST
	 */
	public void fileBytesResponse(Document message) 
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
		sendMessage(doc);
		log.info("sending to " + connectedHost + ":" + connectedPort + 
			doc.toJson());
	}

	/**
	 * @author laif1
	 * 
	 * @param message the content of FILE_DELETE_REQUEST
	 */
	public void fileDeleteResponse(Document message) {
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
			sendMessage(doc);
			log.info("sending to " + connectedHost + ":" + connectedPort + 
				doc.toJson());
			return;
		}

		if (!ServerMain.fileSystemManager.fileNameExists(pathName)) {
			doc.append("message", "pathname does not exist");
			doc.append("status", false);
			sendMessage(doc);
			log.info("sending to " + connectedHost + ":" + connectedPort + 
				doc.toJson());
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
		sendMessage(doc);
		log.info("sending to " + connectedHost + ":" + connectedPort + 
			doc.toJson());
	}
	
	/**
	 * @author laif1
	 * 
	 * @param message the content of FILE_DELETE_REQUEST
	 */
	public void fileModifyResponse(Document message) 
		throws IOException, NoSuchAlgorithmException {
		String pathName = message.getString("pathName");
		System.out.println(pathName);
		Document fileDescriptor = (Document) message.get("fileDescriptor");
		String md5 = fileDescriptor.getString("md5");
		long lastModified = fileDescriptor.getLong("lastModified");

		boolean modifyLoaderStatus = 
			ServerMain.fileSystemManager.modifyFileLoader(pathName, md5, lastModified);

		Document doc = new Document();
		doc.append("command", "fileModifyResponse");
		doc.append("fileDescriptor", fileDescriptor);
		doc.append("pathName", pathName);

		if (!ServerMain.fileSystemManager.isSafePathName(pathName)) {
			doc.append("message", "unsafe pathname given");
			doc.append("status", false);
			sendMessage(doc);
			log.info("sending to " + connectedHost + ":" + connectedPort + 
				doc.toJson());
			return;
		}

		if (!ServerMain.fileSystemManager.fileNameExists(pathName)) {
			doc.append("message", "pathname does not exist");
			doc.append("status", false);
			sendMessage(doc);
			log.info("sending to " + connectedHost + ":" + connectedPort + 
				doc.toJson());
			return;
		}

		if (modifyLoaderStatus) {
			try {
				if (ServerMain.fileSystemManager.checkShortcut(pathName)) {
					doc.append("message", 
						"file already exists with matching content");
					doc.append("status", true);
					sendMessage(doc);
				} else {
					System.out.print("It works");
					doc.append("message", "file loader ready");
					doc.append("status", true);
					sendMessage(doc);
					System.out.print("It works modify");
					fileBytesRequest(message); // need to test
				}
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			log.info("sending to " + connectedHost + ":" + connectedPort + 
				doc.toJson());
			return;
		} else {
			doc.append("message", "there was a problem modifying the file");
			doc.append("status", false);
			sendMessage(doc);
			log.info("sending to " + connectedHost + ":" + connectedPort + 
				doc.toJson());
			return;
		}
	}

	/**
	 * @author laif1
	 */
	public void directoryCreateResponse(Document message) {
		String pathName = message.getString("pathName");

		Document doc = new Document();
		doc.append("command", "DIRECTORY_CREATE_RESPONSE");
		doc.append("pathName", message.getString("pathName"));
		
		if (!ServerMain.fileSystemManager.isSafePathName(pathName)) {
			doc.append("message", "unsafe pathname given");
			doc.append("status", false);
			sendMessage(doc);
			log.info("sending to " + connectedHost + ":" + connectedPort + 
				doc.toJson());
			return;
		}

		if (ServerMain.fileSystemManager.fileNameExists(pathName)) {
			doc.append("message", "pathname already exists");
			doc.append("status", false);
			sendMessage(doc);
			log.info("sending to " + connectedHost + ":" + connectedPort + 
				doc.toJson());
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
		sendMessage(doc);
		log.info("sending to " + connectedHost + ":" + connectedPort + 
			doc.toJson());
	}

	/**
	 * @author laif1
	 * 
	 * @param message the content of DIRECTORY_DELETE_REQUEST
	 */
	public void directoryDeleteResponse(Document message) {
		String pathName = message.getString("pathName");
		
		Document doc = new Document();
		doc.append("command", "DIRECTORY_DELETE_RESPONSE");
		doc.append("pathName", message.getString("pathName"));
		
		if (!ServerMain.fileSystemManager.isSafePathName(pathName)) {
			doc.append("message", "unsafe pathname given");
			doc.append("status", false);
			sendMessage(doc);
			log.info("sending to " + connectedHost + ":" + connectedPort + 
				doc.toJson());
			return;
		}

		if (!ServerMain.fileSystemManager.dirNameExists(pathName)) {
			doc.append("message", "directory name does not exist");
			doc.append("status", false);
			sendMessage(doc);
			log.info("sending to " + connectedHost + ":" + connectedPort + 
				doc.toJson());
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
		sendMessage(doc);
		log.info("sending to " + connectedHost + ":" + connectedPort + 
			doc.toJson());
	}
}