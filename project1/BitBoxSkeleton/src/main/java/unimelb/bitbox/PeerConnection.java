package unimelb.bitbox;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Connection;
import unimelb.bitbox.util.Document;


/**
 * Deal with things about socket including sending and receiving message.
 */
public class PeerConnection extends Connection {
	public enum CONNECTION_STATUS {WAITING, ONLINE, OFFLINE};
	protected Logger log = Logger.getLogger(PeerConnection.class.getName());
	protected volatile CONNECTION_STATUS connectionStatus = CONNECTION_STATUS.WAITING;
	protected ServerMain server = null;
	protected long blockSize;
	protected String host;
	protected int port;
	protected String connectedHost;
	protected int connectedPort;
	private ArrayList<UDPHandlingErrors> handlingErrorsList = new ArrayList<UDPHandlingErrors>(); 
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
	 * UDP connection
	 */
	public PeerConnection(ServerMain server, String connectedHost, int connectedPort, Document doc) throws IOException {
		setCommonAttributesValue(server);
		this.connectedHost = connectedHost;
		this.connectedPort = connectedPort;
		checkCommand(doc);
	}
	/**
	 * try UDP connection and send HANDSHAKE_REQUEST
	 */
	public PeerConnection(ServerMain server, String connectedHost, int connectedPort) throws IOException {
		setCommonAttributesValue(server);
		this.connectedHost = connectedHost;
		this.connectedPort = connectedPort;
		Command.handshakeRequest(this);
	}
	
	/**
	 * Set common attributes value for constructor.
	 */
	private void setCommonAttributesValue(ServerMain server) 
		throws IOException {
		this.server = server;
		host = Configuration.getConfigurationValue("advertisedName");
		if (ServerMain.communicationMode.equals(ServerMain.TCP_MODE)) {
			port = Integer.parseInt(Configuration.getConfigurationValue("port"));
		} else if (ServerMain.communicationMode.equals(ServerMain.UDP_MODE)) {
			port = Integer.parseInt(Configuration.getConfigurationValue("udpPort"));
		}
		blockSize = 
			Long.parseLong(Configuration.getConfigurationValue("blockSize"));
	}

	public void run() {
		if (ServerMain.communicationMode.equals(ServerMain.TCP_MODE)) {
			String data;
			try {
				while ((data = reader.readLine()) != null) {
					// convert message from string to JSON
					Document doc = Document.parse(data);
					checkCommand(doc);
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				// e.printStackTrace();
					server.connectedPeerListRemove(connectedHost + ":" 
						+ connectedPort);
					connectionStatus = CONNECTION_STATUS.OFFLINE;
					try {
						connectedSocket.close();
					} catch (IOException e1) {
					}
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
		log.info("receive "+ doc.toJson() + " from peer");
		server.checkConnectedPorts();
		if (ServerMain.communicationMode.equals(ServerMain.UDP_MODE)) {
			// previous command didn't arrive
			UDPPacketLossProtection(doc);
		}
		String command = doc.getString("command");
		if (connectionStatus == CONNECTION_STATUS.WAITING || connectionStatus == CONNECTION_STATUS.OFFLINE) {
			switch (command) {
			case "HANDSHAKE_REQUEST":
				Command.handshakeRequestHandler(this, doc);
				break;
			case "HANDSHAKE_RESPONSE":
				Command.handshakeResponseHandler(this, doc);
				break;
			case "CONNECTION_REFUSED":
				Command.connectionRefusedHandler(this);
				break;
			case "INVALID_PROTOCOL":
				Command.invalidProtocolHandler(this);
				break;
			default:
				Command.invalidProtocol(this);
				break;
			}
		}
		// connectionStatus == CONNECTION_STATUS.ONLINE
		else {
			switch (command) {
			case "INVALID_PROTOCOL":
				Command.invalidProtocolHandler(this);
				break;
			case "FILE_CREATE_RESPONSE":
				break;
			case "FILE_CREATE_REQUEST":
				Command.fileCreateRequestHandler(this, doc);
				break;
			case "FILE_BYTES_REQUEST":
				Command.fileBytesRequestHandler(this, doc);
				break;
			case "FILE_BYTES_RESPONSE":
				Command.fileBytesResponseHandler(this, doc);
				break;
			case "FILE_DELETE_REQUEST":
				Command.fileDeleteRequestHandler(this, doc);
				break;
			case "FILE_DELETE_RESPONSE":
				break;
			case "FILE_MODIFY_REQUEST":
				Command.fileModifyRequestHandler(this, doc);
				break;
			case "FILE_MODIFY_RESPONSE":
				break;
			case "DIRECTORY_CREATE_REQUEST":
				Command.directoryCreateRequestHandler(this, doc);
				break;
			case "DIRECTORY_CREATE_RESPONSE":
				break;
			case "DIRECTORY_DELETE_REQUEST":
				Command.directoryDeleteRequestHandler(this, doc);
			case "DIRECTORY_DELETE_RESPONSE":
				break;
			case "CONNECTION_REFUSED":
				Command.connectionRefusedHandler(this);
				break;
			default:
				Command.invalidProtocol(this);
			}
		}
	}

	
	/**
	 * get connected peer info
	 * @author yuqiangz@student.unimelb.edu.au
	 */
	public ArrayList<Document> getConnectedPeerDocumentArrayList() {
		server.checkConnectedPorts();
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
		return peerDocList;
	}
	
	/**
	 * calculate the safe length of file peer can request in one time under UDP 
	 * mode
	 * @param doc
	 * @param fileSize
	 * @return
	 */
	public long getReadFileLength(Document doc, long startPos, long fileSize) {
		long lastFileSize = fileSize - startPos;
		//long lastLength = fileSize - startPos > blockSize ? blockSize : fileSize - startPos;
		Document sample = new Document();
		sample.append("command", "FILE_BYTES_RESPONSE");
		sample.append("fileDescriptor", (Document)doc.get("fileDescriptor"));
		sample.append("pathName", doc.getString("pathName"));
		sample.append("position", fileSize);
		sample.append("length", fileSize);
		sample.append("content", "");
		sample.append("message", "successful read");
		sample.append("status", true);
		long spareEncodedSize = blockSize - sample.toJson().length();
		long spareOriginalSize = spareEncodedSize / 4 * 3 - 100; // for safe reason, need more think!!!
		return spareEncodedSize > lastFileSize ? lastFileSize : spareOriginalSize;
	}
		
	/**
	 * wait for more comment*********************************
	 */
	@Override
	public void sendMessage(Document doc) {
		log.info("Sending " + doc.toJson() + " to " + connectedHost + ":" + connectedPort);
		if (ServerMain.communicationMode.equals(ServerMain.TCP_MODE)) {
			try {
				writer.write(doc.toJson() + "\n");
				writer.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				log.info("Fail to send message to connected peer.");
			}
		} else if (ServerMain.communicationMode.equals(ServerMain.UDP_MODE)) {
			InetAddress destHostInetAddress;
			try {
				destHostInetAddress = InetAddress.getByName(connectedHost);
				byte[] replyBytes = (doc.toJson() + "\n").getBytes();
				DatagramPacket reply= new DatagramPacket(replyBytes, doc.toJson().length(), destHostInetAddress, connectedPort);
				server.UDPSocket.send(reply);
				storeCommandForUDPPacketLossProtection(doc);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				log.info("Fail to send message to connected peer." + connectedHost + ":" + connectedPort);
			}
		}
	}
	
	public CONNECTION_STATUS getConnectionStatus() {
		return connectionStatus;
	}
	
	public void setConnectionStatus(CONNECTION_STATUS newStatus) {
		connectionStatus = newStatus;
	}
	
	public ServerMain getServer() {
		return server;
	}
	
	public String getConnectedHost() {
		return connectedHost;
	}
	
	public void setConnectedHost(String connectedHost) {
		this.connectedHost = connectedHost;
	}
	
	public void setConnectedPort(int connectedPort) {
		this.connectedPort = connectedPort;
	}
	
	/**
	 * only for UDP mode
	 * @param doc
	 */
	public void UDPPacketLossProtection(Document doc) {
		String receivedCommand = doc.getString("command");
		UDPHandlingErrors stored = null;
		switch(receivedCommand) {
		case "CONNECTION_REFUSED":
		case "HANDSHAKE_RESPONSE": {
				for(int i = 0; i < handlingErrorsList.size(); i++) {
					stored = handlingErrorsList.get(i);
					if (stored.storedDoc == null) continue;
					if (stored.storedDoc.getString("command").equals("HANDSHAKE_REQUEST")) {
						handlingErrorsList.remove(i);
						stored.UDPTimer.cancel();
						handlingErrorsList.trimToSize();
						break;
					}
				}
				break;
			}
		case "FILE_CREATE_RESPONSE": {
				for(int i = 0; i < handlingErrorsList.size(); i++) {
					stored = handlingErrorsList.get(i);
					if (stored.storedDoc == null) continue;
					if (stored.storedDoc.getString("command").equals("FILE_CREATE_REQUEST") && doc.getString("pathName").equals(stored.storedDoc.getString("pathName"))) {
						handlingErrorsList.remove(i);
						stored.UDPTimer.cancel();
						handlingErrorsList.trimToSize();
						break;
					}
				}
				break;
			}
		case "FILE_BYTES_RESPONSE": {
				for(int i = 0; i < handlingErrorsList.size(); i++) {
					stored = handlingErrorsList.get(i);
					if (stored.storedDoc == null) continue;
					if (stored.storedDoc.getString("command").equals("FILE_BYTES_REQUEST") && doc.getString("pathName").equals(stored.storedDoc.getString("pathName"))) {
						String temp = "" + doc.get("position");
						long response = Long.parseLong(temp);
						temp = "" + stored.storedDoc.get("position");
						long request = Long.parseLong(temp);
						if (response - request == 0) {
							handlingErrorsList.remove(i);
							stored.UDPTimer.cancel();
							handlingErrorsList.trimToSize();
							break;
						}
					}
				}
				break;
			}
		case "FILE_BYTES_REQUEST": {
			for (int i = 0; i < handlingErrorsList.size(); i++) {
				stored = handlingErrorsList.get(i);
				if (stored.storedDoc == null)
					continue;
				if (stored.storedDoc.getString("command").equals("FILE_BYTES_RESPONSE")
						&& doc.getString("pathName").equals(stored.storedDoc.getString("pathName"))) {
					String temp = "" + doc.get("position");
					long requestPosition = Long.parseLong(temp);
					temp = "" + stored.storedDoc.get("position");
					long lastResponsePosition = Long.parseLong(temp);
					temp = "" + stored.storedDoc.get("length");
					long lastResponseLength = Long.parseLong(temp);
					if (requestPosition == lastResponsePosition + lastResponseLength) {
						handlingErrorsList.remove(i);
						stored.UDPTimer.cancel();
						handlingErrorsList.trimToSize();
						break;
					}
				}
			}
			break;
			}
		case "FILE_DELETE_RESPONSE": {
			for(int i = 0; i < handlingErrorsList.size(); i++) {
				stored = handlingErrorsList.get(i);
				if (stored.storedDoc == null) continue;
				if (stored.storedDoc.getString("command").equals("FILE_DELETE_REQUEST") && doc.getString("pathName").equals(stored.storedDoc.getString("pathName"))) {
					handlingErrorsList.remove(i);
					stored.UDPTimer.cancel();
					handlingErrorsList.trimToSize();
					break;
				}
			}
			break;
		}
		case "FILE_MODIFY_RESPONSE": {
			for(int i = 0; i < handlingErrorsList.size(); i++) {
				stored = handlingErrorsList.get(i);
				if (stored.storedDoc == null) continue;
				if (stored.storedDoc.getString("command").equals("FILE_MODIFY_REQUEST") && doc.getString("pathName").equals(stored.storedDoc.getString("pathName"))) {
					handlingErrorsList.remove(i);
					stored.UDPTimer.cancel();
					handlingErrorsList.trimToSize();
					break;
				}
			}
			break;
		}
		case "DIRECTORY_DELETE_RESPONSE": {
			for(int i = 0; i < handlingErrorsList.size(); i++) {
				stored = handlingErrorsList.get(i);
				if (stored.storedDoc == null) continue;
				if (stored.storedDoc.getString("command").equals("DIRECTORY_DELETE_REQUEST") && doc.getString("pathName").equals(stored.storedDoc.getString("pathName"))) {
					handlingErrorsList.remove(i);
					stored.UDPTimer.cancel();
					handlingErrorsList.trimToSize();
					break;
				}
			}
			break;
		}
		case "DIRECTORY_CREATE_RESPONSE": {
			for(int i = 0; i < handlingErrorsList.size(); i++) {
				stored = handlingErrorsList.get(i);
				if (stored.storedDoc == null) continue;
				if (stored.storedDoc.getString("command").equals("DIRECTORY_CREATE_REQUEST") && doc.getString("pathName").equals(stored.storedDoc.getString("pathName"))) {
					handlingErrorsList.remove(i);
					stored.UDPTimer.cancel();
					handlingErrorsList.trimToSize();
					break;
				}
			}
			break;
		}
		}
	}
	
	private void storeCommandForUDPPacketLossProtection(Document doc) {
		String command = doc.getString("command");
		switch(command) {
		case "CONNECTION_REFUSED":
		case "HANDSHAKE_RESPONSE":
		case "FILE_CREATE_RESPONSE":
		case "FILE_DELETE_RESPONSE":
		case "FILE_MODIFY_RESPONSE":
		case "DIRECTORY_CREATE_RESPONSE":
		case "DIRECTORY_DELETE_RESPONSE":
		case "INVALID_PROTOCOL": break;
		case "FILE_BYTES_RESPONSE": {
			String temp = "" + doc.get("position");
			long position = Long.parseLong(temp);
			temp = "" + doc.get("length");
			long length = Long.parseLong(temp);
			Document fileDescriptor = (Document)(doc.get("fileDescriptor"));
			temp = "" + fileDescriptor.get("fileSize");
			long fileSize = Long.parseLong(temp);
			if (fileSize == length + position) {
				break;
			}
		}
		default:
			handlingErrorsList.add(new UDPHandlingErrors(doc, this));
			break;
		}		
		return;
	}
}