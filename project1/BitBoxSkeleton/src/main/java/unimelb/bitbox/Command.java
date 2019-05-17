package unimelb.bitbox;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;

import unimelb.bitbox.PeerConnection.CONNECTION_STATUS;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;
/**
 * Include all bitbox command and command handler
 * @author yuqiangz@student.unimelb.edu.au
 *
 */
public class Command {
	public static void handshakeRequestHandler(PeerConnection connection, Document handshakeRequestDoc) throws IOException {
		connection.server.checkConnectedPorts();
		Document hostPort = (Document) handshakeRequestDoc.get("hostPort");
		// System.out.println(hostPort.toJson());
		connection.connectedHost = hostPort.getString("host");
		String temp = "" + hostPort.get("port");
		connection.connectedPort = Integer.parseInt(temp);
		if (connection.server.connectedPeerListContains(connection.connectedHost + ":" + connection.connectedPort)) {
			connection.connectionStatus = CONNECTION_STATUS.OFFLINE;
			invalidProtocol(connection);
		} else if (ServerMain.currentIncomingconnectionNum >= ServerMain.maximunIncommingConnections) {
			connection.connectionStatus = CONNECTION_STATUS.OFFLINE;
			connectionRefused(connection);
		} else {
			connection.connectionStatus = CONNECTION_STATUS.ONLINE;
			handshakeResponse(connection);
		}
	}
	
	public void handshakeResponseHandler(PeerConnection connection, Document handshakeResponseDoc) {
		Document hostPort = (Document) handshakeResponseDoc.get("hostPort");
		// System.out.println(hostPort.toJson());
		connection.connectedHost = hostPort.getString("host");
		String temp = "" + hostPort.get("port");
		connection.connectedPort = Integer.parseInt(temp);
		// mark as successful connection
		if (connection.server.connectedPeerListPut(connection.connectedHost + ":" + connection.connectedPort, connection) == false) {
			connection.connectionStatus = CONNECTION_STATUS.OFFLINE;
			if (connection.server.communicationMode.equals(ServerMain.TCP_MODE)) {
				try {
					connection.getConnectedSocket().close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} else {
			connection.connectionStatus = CONNECTION_STATUS.ONLINE;
		}
		// sync at the beginning of a successful connection
		for (FileSystemEvent pathEvent : ServerMain.fileSystemManager.generateSyncEvents()) {
			// log.info(pathEvent.toString());
			connection.server.processFileSystemEvent(pathEvent);
		}
	}
	
	public static void connectionRefusedHandler(PeerConnection connection) {
		connection.connectionStatus = CONNECTION_STATUS.OFFLINE;
		if (connection.server.communicationMode.equals(ServerMain.TCP_MODE)) {
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
		if (connection.server.communicationMode.equals(ServerMain.TCP_MODE)) {
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
			connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort + 
				doc.toJson());
			return;
		}
		//log.info(pathName + " is safe path name");
		if (ServerMain.fileSystemManager.fileNameExists(pathName)) {
			doc.append("message", "pathname already exists");
			doc.append("status", false);
			connection.sendMessage(doc);
			connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort + 
				doc.toJson());
			return;
		}
		//log.info(pathName + " doesn't exist bebore.");
		try {
			if (ServerMain.fileSystemManager.createFileLoader(pathName, md5, 
				length, lastModified)) {
				connection.log.info("create file loader successfully!");
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
				connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort + 
					doc.toJson());
				return;
			} else {
				doc.append("message", "there was a problem creating the file");
				doc.append("status", false);
				connection.sendMessage(doc);
				connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort + 
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
		connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort + 
			doc.toJson());
	}
	
	/**
	 * @author yuqiangz@student.unimelb.edu.au
	 */
	public static void invalidProtocol(PeerConnection connection) {
		Document doc = new Document();
		doc.append("command", "INVALID_PROTOCOL");
		doc.append("message", "message must a command field as string");
		connection.sendMessage(doc);
		connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort + 
			doc.toJson());
		try {
			connection.server.connectedPeerListRemove(connection.connectedHost + ":" + connection.connectedPort);
			if (connection.server.communicationMode.equals(ServerMain.TCP_MODE)) {
				connection.getConnectedSocket().close();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		connection.server.connectedPeerListRemove(connection.connectedHost + ":" + connection.connectedPort);
		connection.connectionStatus = CONNECTION_STATUS.OFFLINE;
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
		connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort +
			doc.toJson());
		connection.connectionStatus = CONNECTION_STATUS.OFFLINE;
		if (connection.server.communicationMode.equals(ServerMain.TCP_MODE)) {
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
		connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort +
			doc.toJson());
		
		
		
		// mark as a successful connection
		if(connection.server.connectedPeerListPut(connection.connectedHost + ":" + connection.connectedPort, 
				connection) == false) {
			if (connection.server.communicationMode.equals(ServerMain.TCP_MODE)) {
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
		if (connection.server.communicationMode.equals(ServerMain.UDP_MODE)) {
			fileBytesResponseUDPHandler(connection, doc);
		} else if (connection.server.communicationMode.equals(ServerMain.TCP_MODE)) {
			connection.sendMessage(doc);
			connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort + 
					doc.toJson());
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
			connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort + 
					originalDoc.toJson());
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
			connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort + 
				doc.toJson());
			return;
		}

		if (!ServerMain.fileSystemManager.fileNameExists(pathName)) {
			doc.append("message", "pathname does not exist");
			doc.append("status", false);
			connection.sendMessage(doc);
			connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort + 
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
		connection.sendMessage(doc);
		connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort + 
			doc.toJson());
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
		doc.append("command", "fileModifyResponse");
		doc.append("fileDescriptor", fileDescriptor);
		doc.append("pathName", pathName);

		if (!ServerMain.fileSystemManager.isSafePathName(pathName)) {
			doc.append("message", "unsafe pathname given");
			doc.append("status", false);
			connection.sendMessage(doc);
			connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort + 
				doc.toJson());
			return;
		}

		if (!ServerMain.fileSystemManager.fileNameExists(pathName)) {
			doc.append("message", "pathname does not exist");
			doc.append("status", false);
			connection.sendMessage(doc);
			connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort + 
				doc.toJson());
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
			connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort + 
				doc.toJson());
			return;
		} else {
			doc.append("message", "there was a problem modifying the file");
			doc.append("status", false);
			connection.sendMessage(doc);
			connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort + 
				doc.toJson());
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
			connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort + 
				doc.toJson());
			return;
		}

		if (ServerMain.fileSystemManager.fileNameExists(pathName)) {
			doc.append("message", "pathname already exists");
			doc.append("status", false);
			connection.sendMessage(doc);
			connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort + 
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
		connection.sendMessage(doc);
		connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort + 
			doc.toJson());
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
			connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort + 
				doc.toJson());
			return;
		}

		if (!ServerMain.fileSystemManager.dirNameExists(pathName)) {
			doc.append("message", "directory name does not exist");
			doc.append("status", false);
			connection.sendMessage(doc);
			connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort + 
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
		connection.sendMessage(doc);
		connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort + 
			doc.toJson());
	}
	
	/**
	 * @author yuqiangz@student.unimelb.edu.au
	 */
	public static void handshakeRequest(PeerConnection connection) {
		Document doc = new Document();
		doc.append("command", "HANDSHAKE_REQUEST");
		doc.append("hostPort", new HostPort(connection.host, connection.port).toDoc());
		connection.sendMessage(doc);
		connection.log.info("sending to " + connection.connectedHost + ":" + connection.connectedPort +
			doc.toJson());
	}
}