package unimelb.bitbox;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

/**
 * Represent server.
 */
public class ServerMain extends Thread implements FileSystemObserver {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	private ServerSocket serverSocket;
	private String host;
	private int port;
	protected FileSystemManager fileSystemManager;
	private ArrayList<String> connectedPeerList;
	protected static int connectionNum = 0;
	protected static int maximunIncommingConnections = 
			Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"));
	
	public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {
		fileSystemManager=new FileSystemManager(Configuration.getConfigurationValue("path"),this);
		host = Configuration.getConfigurationValue("advertisedName");
		port = Integer.parseInt(Configuration.getConfigurationValue("port"));
		connectedPeerList = new ArrayList<String>();
		try {
			serverSocket = new ServerSocket(port);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		start();
	}
	
	public ArrayList<String> getConnectedPeerList() {
		return new ArrayList<String>(connectedPeerList);
	}
	
	public void addConnectedPeerList(String peer){
		connectedPeerList.add(peer);
	}
	
	public Boolean connectedPeerListContains(String peer) {
		return connectedPeerList.contains(peer);
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
	}
	
}
