package unimelb.bitbox;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class ServerMain extends Thread implements FileSystemObserver {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	private ServerSocket serverSocket;
	private String serverHost;
	private int serverPort;
	protected FileSystemManager fileSystemManager;
	
	public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {
		fileSystemManager=new FileSystemManager(Configuration.getConfigurationValue("path"),this);
		
		// Yuqiang
		serverHost = Configuration.getConfigurationValue("advertisedName");
		serverPort = Integer.parseInt(Configuration.getConfigurationValue("port"));
		try {
			serverSocket = new ServerSocket(serverPort);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void run() {
		while(true) {
			Socket clientSocket;
			try {
				clientSocket = serverSocket.accept();
				new Connection(clientSocket);
				log.info("get connect request from " + clientSocket.getInetAddress().getHostName() 
						+ clientSocket.getPort());
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
