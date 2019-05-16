package unimelb.bitbox;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import unimelb.bitbox.PeerConnection.CONNECTION_STATUS;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class ServerMain extends Thread implements FileSystemObserver {
	public final static String TCP_MODE = "tcp";
	public final static String UDP_MODE = "udp";
	protected String communicationMode;
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	protected static FileSystemManager fileSystemManager;
	private ServerSocket serverSocket;
	protected DatagramSocket UDPSocket;
	/**
	 * Assume every peer's name(host:port) is different, key is Peer's name,
	 * collect objects of class Connection after passing the handshake process.
	 */
	private volatile HashMap<String, PeerConnection> connectedPeerList;
	protected volatile static int currentIncomingconnectionNum = 0;
	protected static int maximunIncommingConnections = Integer.parseInt(
		Configuration.getConfigurationValue("maximumIncommingConnections"));
	
	public ServerMain() 
		throws NumberFormatException, IOException, NoSuchAlgorithmException {
		fileSystemManager = new FileSystemManager(
			Configuration.getConfigurationValue("path"),this);
		connectedPeerList = new HashMap<String, PeerConnection>();
		communicationMode = Configuration.getConfigurationValue("mode");
		// set server to receive incoming connections	
		try {
			if (communicationMode.equals(TCP_MODE)) {
				int TCPPort = Integer.parseInt(Configuration.getConfigurationValue("port"));
				serverSocket = new ServerSocket(TCPPort);
				log.info("BitBox Peer in TCP mode");
			} else if (communicationMode.equals(UDP_MODE)) {
				int UDPPort = Integer.parseInt(Configuration.getConfigurationValue("udpPort"));
				InetAddress hostInetAddress = InetAddress.getByName(Configuration.getConfigurationValue("advertisedName"));
				UDPSocket = new DatagramSocket(UDPPort, hostInetAddress);
				log.info("BitBox Peer in UDP mode, host:" + hostInetAddress.getHostName() + ", port:" + UDPPort);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// ready to receive incoming connections
		start();
		
		// try to connect peers
		connectPeer();	
		
		// Every specified seconds, sync with all connected peers
		syncWithPeers();
	}
	
	
	private void connectPeer() {
		synchronized (connectedPeerList) {
			for (String peer : 
				Configuration.getConfigurationValue("peers").split(",")) {
				// already connected
				if (connectedPeerList.containsKey(peer)) {
					continue;
				}
				
				String destHost = (peer.split(":"))[0];
				int destPort;
				try {
					destPort = Integer.parseInt((peer.split(":"))[1]);
				} catch (Exception e) {
					continue;
				}				
				connectGivenPeer(destHost, destPort);
			}
		}
	}
	
	public PeerConnection connectGivenPeer(String host, int port) {
		if (communicationMode.equals(TCP_MODE)) {
			try {
				Socket clientSocket;
				clientSocket = new Socket(host, port);
				log.info("connect to " + host + ":" + port + " and wait for handshake identification");
				PeerConnection connection = new PeerConnection(this, clientSocket, host, port);
				// send HANDSHAKE_REQUEST
				connection.handshakeRequest();
				return connection;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				log.warning("while connecting to " + host + ":" + port + " refused.");
				return null;
			}
		} else if (communicationMode.equals(UDP_MODE)) {
			PeerConnection connection = null;
			try {
				connection = new PeerConnection(this, host, port);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return connection;
		}
		return null;
	}

	/**
	 * 
	 * @return a copy of connectedPeerList
	 */
	public HashMap<String, PeerConnection> getConnectedPeerList() {
		return new HashMap<String, PeerConnection>(connectedPeerList);
	}

	public synchronized Boolean connectedPeerListPut(
		String peer, PeerConnection connection) {
		if(connectedPeerList.containsKey(peer)) {
			return false;
		} else {
			connectedPeerList.put(peer, connection);
			// update the num of incoming connection
			currentIncomingconnectionNum++;
			log.info("add " + peer + " into connectedPeerList.");
			return true;
		}	
	}
	
	public synchronized Boolean connectedPeerListContains(String peer) {
		return connectedPeerList.containsKey(peer);
	}
	
	public synchronized void connectedPeerListRemove(String peer) {
		if(connectedPeerList.containsKey(peer)) {
			// update the num of incoming connection
			currentIncomingconnectionNum--;
			connectedPeerList.get(peer).setConnectionStatus(CONNECTION_STATUS.OFFLINE);
			connectedPeerList.remove(peer);
		}
	}
	
	/**
	 * Every specified seconds, sync with all connected peers
	 * 
	 * @author yuqiangz@student.unimelb.edu.au
	 */
	public void syncWithPeers() {
		/*
		Timer timer = new Timer();
		long syncPeriod = 
			Long.parseLong(Configuration.getConfigurationValue("syncInterval"))
			* 1000;
		timer.schedule(new TimerTask() {
			public void run() {
				log.info("sync with all connected peers");
				for(FileSystemEvent pathevent : 
					fileSystemManager.generateSyncEvents()) {
					//log.info(pathevent.toString());
					processFileSystemEvent(pathevent);
				}
				checkConnectedPorts();
			}
		}, syncPeriod, syncPeriod);
		*/
	}
	
	/**
	 * Check whether some ports are occupied by bad connections, and delete it 
	 * from connectedPeerList.
	 * 
	 * @author yuqiangz@student.unimelb.edu.au
	 */
	public void checkConnectedPorts() {
		// check whether some ports are occupied by bad connections
		for (String peer:connectedPeerList.keySet()) {
			if (connectedPeerList.get(peer).getConnectedSocket().isClosed() ==
				true) {
				connectedPeerList.get(peer).setConnectionStatus(CONNECTION_STATUS.OFFLINE);
				connectedPeerListRemove(peer);
			}
		}
	}
	
	public void run() {
		while (communicationMode.equals(TCP_MODE)) {
			while (true) {
				Socket clientSocket;
				try {
					// wait for receive connection
					clientSocket = serverSocket.accept();
					new PeerConnection(this, clientSocket);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		while (communicationMode.equals(UDP_MODE)) {
			int bufferSize = Integer.parseInt(Configuration.getConfigurationValue("blockSize"));	
			while (true) {
				log.info("wait for message");
				try {
					byte[] buffer = new byte[bufferSize];
					DatagramPacket request = new DatagramPacket(buffer, buffer.length);
					UDPSocket.receive(request);
					String requestHost = getHost(request.getAddress());
					int requestPort = request.getPort();
					Document extractDoc = extractDocument(request);
					log.info("**UDP**: receice a message:" + extractDoc.toJson() + " from the host:" + requestHost + ", prot:" + requestPort);
					UDPConnectionHandler(requestHost, requestPort, request);//**********
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	public static String getHost(InetAddress address) {
		try {
			InetAddress localhost = InetAddress.getByName("localhost");
			if (address.getHostAddress().equals(localhost.getHostAddress())) {
				return "localhost";
			} else {
				return address.getHostName();
			}
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			return address.getHostName();
		}

	}
	
	private void UDPConnectionHandler(String requestHost, int requestPort, DatagramPacket request) {
		PeerConnection connection = connectedPeerList.get(requestHost + ":" + requestPort);
		if(connection == null) {
			try {
				new PeerConnection(this, requestHost, requestPort, request);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			try {
				connection.checkCommand(extractDocument(request));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
		// TODO: process events
		switch (fileSystemEvent.event) {
			case FILE_CREATE: {
				fileCreateRequest(fileSystemEvent);
				break;
			}
			case FILE_DELETE: {
			    fileDeleteRequest(fileSystemEvent);
				break;
			}
			case FILE_MODIFY: {
			    fileModifyRequest(fileSystemEvent);
				break;
			}
			case DIRECTORY_CREATE: {
			    directoryCreateRequest(fileSystemEvent);
				break;
			}
			case DIRECTORY_DELETE: {
			    directoryDeleteRequest(fileSystemEvent);
				break;
			}
		}
	}
	
	/**
	 * Broadcast a message to every connected peer.
	 * 
	 * @param doc the message
	 */
	private void broadcastToPeers(Document doc) {
		for(String peer: connectedPeerList.keySet()) {
			connectedPeerList.get(peer).sendMessage(doc);
			log.info("sending to " + peer + doc.toJson());
		}
	}
	
	/**
	 * @author yuqiangz@student.unimelb.edu.au
	 */
	public void fileCreateRequest(FileSystemEvent fileSystemEvent) {
		Document doc = new Document();
		doc.append("command", "FILE_CREATE_REQUEST");
		doc.append("fileDescriptor", fileSystemEvent.fileDescriptor.toDoc()); 
		doc.append("pathName", fileSystemEvent.pathName);

		if(fileSystemEvent.pathName.endsWith("(bitbox)")) {
			log.info("It's suffix file.");
			return;
		}
		broadcastToPeers(doc);
	}

	/**
     * @author laif1
     */
	public void fileDeleteRequest(FileSystemEvent fileSystemEvent) {
	    //System.out.print("deleted request used");
	    Document doc = new Document();
	    doc.append("command", "FILE_DELETE_REQUEST");
	    doc.append("fileDescriptor", fileSystemEvent.fileDescriptor.toDoc());
	    doc.append("pathName", fileSystemEvent.pathName);
        broadcastToPeers(doc);
	}
	
	/**
     * @author laif1
     */
	public void fileModifyRequest(FileSystemEvent fileSystemEvent) {
	    Document doc = new Document();
        doc.append("command", "FILE_MODIFY_REQUEST");
        doc.append("fileDescriptor", fileSystemEvent.fileDescriptor.toDoc());
        doc.append("pathName", fileSystemEvent.pathName);
        broadcastToPeers(doc);
	}
	
	/**
     * @author laif1
     */
	public void directoryCreateRequest(FileSystemEvent fileSystemEvent) {
	    Document doc = new Document();
        doc.append("command", "DIRECTORY_CREATE_REQUEST");
        doc.append("pathName", fileSystemEvent.pathName);
        broadcastToPeers(doc);
	}
	
	/**
     * @author laif1
     */
	public void directoryDeleteRequest(FileSystemEvent fileSystemEvent) {
        Document doc = new Document();
        doc.append("command", "DIRECTORY_DELETE_REQUEST");
        doc.append("pathName", fileSystemEvent.pathName);
        broadcastToPeers(doc);
    }
	
	/**
	 * disconnect the connected peer
	 * @return	true: disconnect the peer
	 * 			false: the given peer is not connected 
	 */
	public boolean disconnectPeer(String host, int port) {
		PeerConnection givenPeerConnection = connectedPeerList.get(host + ":" + port);
		if (givenPeerConnection == null) {
			return false;
		}
		try {
			givenPeerConnection.getConnectedSocket().close();
			givenPeerConnection.setConnectionStatus(CONNECTION_STATUS.OFFLINE);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		connectedPeerListRemove(host + ":" + port);
		return true;
	}
	
	/**
	 * extract the received Document object from DatagramPacket
	 */
	public static Document extractDocument(DatagramPacket request) {
		String originalContent = new String(request.getData());
		int endIndex = originalContent.lastIndexOf("}") + 1;
		String extractContent = originalContent.substring(0, endIndex);
		return Document.parse(extractContent);
	}
	
}
