package unimelb.bitbox.util;

import org.kohsuke.args4j.Option;

public class ClientCmdLineArgs {
	@Option(required = true, name = "-c", usage = "Command")
	private String command;
	
	@Option(required = true, name = "-s", usage = "Server")
	private String server;

	@Option(required = false, name = "-p", usage = "Specified Peer")
	private String specifiedPeer;
	
	public String getCommand() {
		return command;
	}
	
	public String getServer() {
		return server;
	}
	
	public String getSpecifiedPeer() {
		return specifiedPeer;
	}
}