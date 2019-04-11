package unimelb.bitbox;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.util.FileSystemManager.EVENT;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

/**
 * Represent server.
 */
public class ServerMain extends Thread implements FileSystemObserver {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	private ServerSocket serverSocket;
	private String host;
	private int port;
	protected static FileSystemManager fileSystemManager;
	/**
	 * Assume every peer's name(host:port) is different, key is Peer's name,
	 * collect objects of class Connection after passing the handshake process.
	 */
	private HashMap<String, Connection> connectedPeerList;
	protected static int connectionNum = 0;
	protected static int maximunIncommingConnections = 
			Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"));
	
	public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {
		fileSystemManager=new FileSystemManager(Configuration.getConfigurationValue("path"),this);
		host = Configuration.getConfigurationValue("advertisedName");
		port = Integer.parseInt(Configuration.getConfigurationValue("port"));
		connectedPeerList = new HashMap<String, Connection>();
		try {
			serverSocket = new ServerSocket(port);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		start();
	}

	public HashMap<String, Connection> getConnectedPeerList(){
		return new HashMap<String, Connection>(connectedPeerList);
	}

	public Boolean connectedPeerListPut(String peer, Connection connection) {
		if(connectedPeerList.containsKey(peer)) {
			return false;
		}
		connectedPeerList.put(peer, connection);
		return true;
	}
	
	public Boolean connectedPeerListContains(String peer) {
		return connectedPeerList.containsKey(peer);
	}
	
	public void connectedPeerListRemove(String peer) {
		connectedPeerList.remove(peer);
	}
	
	public void run() {
		while(true) {
			Socket clientSocket;
			try {
				// wait for receive connection
				clientSocket = serverSocket.accept();
				new Connection(this, clientSocket);
				//log.info("get connect request from " + clientSocket.getInetAddress().getHostName() 
					//	+ clientSocket.getPort());
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
		// delete it after debug
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
	
}
