package unimelb.bitbox;

import org.kohsuke.args4j.CmdLineParser;

import java.util.logging.Logger;
import java.io.BufferedReader;
import java.io.FileReader;
import java.net.Socket;
import java.nio.file.FileSystems;

import org.kohsuke.args4j.CmdLineException;
import unimelb.bitbox.util.ClientCmdLineArgs;

/**
 * Client class is designed for 1st part of project2. After it is authorized by 
 * connected peer, it can manage the peer's connection from the command line.
 * Launch method: java -cp bitbox.jar unimelb.bitbox.Client -c youCommand -s serverPort -p givenPeer
 * Note: -p is optional.
 * 
 * @author yuqiangz@student.unimelb.edu.au
 *
 */
public class Client{
	public final static String LIST_PEERS = "list_peers";
	public final static String CONNECT_PEER = "connect_peer";
	public final static String DISCONNECT_PEER = "disconnect_peer";
	public final static String[] CLIENT_COMMAND = {LIST_PEERS, CONNECT_PEER, DISCONNECT_PEER};
	protected static Logger log = Logger.getLogger(Client.class.getName());
	private String clientCommand = null;
	private String serverHost = null;
	private int serverPort = 0;
	private String givenPeerHost = null;
	private int givenPeerPort = 0;
	private String identity = null;
	
	private Client(String[] args) {
		setIdentity();
		//log.info("Client identity is " + identity);
		if(getCommand(args) == false) {
			log.info("STOP: error commands.");
			System.exit(1);
		}
		try {
			Socket clientSocket = new Socket(serverHost, serverPort);
			new ClientConnection(this, clientSocket);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			log.warning("while connecting to " + serverHost + ":" + serverPort + " refused.");
		}
	}
	
	public static void main(String[] args) {
		log.info("BitBox Client starting...");
		new Client(args);
	}
	
	private boolean getCommand(String[] args) {
		ClientCmdLineArgs argsBean = new ClientCmdLineArgs();
		CmdLineParser parser = new CmdLineParser(argsBean);
		try {			
			// parse the arguments
			parser.parseArgument(args);	
			// check input the compulsory parameters
			if (argsBean.getCommand() == null || argsBean.getServer() == null) {
				parser.printUsage(System.err);
				return false;
			}
			
			// get client command
			boolean commandFlag = false;
			String clientCommand = argsBean.getCommand();
			for (int i = 0; i < CLIENT_COMMAND.length; i++) {
				if (clientCommand.equals(CLIENT_COMMAND[i])) {
					commandFlag = true;
					break;
				}
			}
			if (commandFlag == false) {
				parser.printUsage(System.err);
				return false;
			} else {
				this.clientCommand = clientCommand;
			}
			
			// get server host and port
			String server = argsBean.getServer();
			serverHost = (server.split(":"))[0];
			serverPort = Integer.parseInt((server.split(":"))[1]);
			
			String specifiedPeer = argsBean.getSpecifiedPeer();
			if (specifiedPeer != null) {
				givenPeerHost = specifiedPeer.split(":")[0];
				givenPeerPort = Integer.parseInt((specifiedPeer.split(":"))[1]);
			}
		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			
			//Print the usage to help the user understand the arguments expected
			//by the program
			parser.printUsage(System.err);
		}
		return true;
	}
		
	/**
	 * get identity from "clientKeystore\bitboxclient_rsa.pub".
	 */
	private void setIdentity() {
		try (BufferedReader br = new BufferedReader(new FileReader("clientKeystore" + FileSystems.getDefault().getSeparator() + "bitboxclient_rsa.pub"))){
			String text = br.readLine();
			identity = text.substring(text.lastIndexOf(" ") + 1);
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
			log.info("Cannot get identity from clientKeystore" + FileSystems.getDefault().getSeparator()+ "bitboxclient_rsa.pub.");
		}
	}
	
	public String getClientCommand() {
		return clientCommand;
	}
	
	public String getServerHost() {
		return serverHost;
	}
	
	public int getServerPort() {
		return serverPort;
	}
	
	public String getGivenPeerHost() {
		return givenPeerHost;
	}
	
	public int getGivenPeerPort() {
		return givenPeerPort;
	}
	
	public String getIdentity() {
		return identity;
	}
}