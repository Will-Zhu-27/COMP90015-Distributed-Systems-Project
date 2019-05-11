package unimelb.bitbox;

import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.CmdLineException;

import unimelb.bitbox.util.ClientCmdLineArgs;

public class Client{
	public static void main(String[] args) {
		ClientCmdLineArgs argsBean = new ClientCmdLineArgs();
		CmdLineParser parser = new CmdLineParser(argsBean);
		try {
			
			//Parse the arguments
			parser.parseArgument(args);
			
			//After parsing, the fields in argsBean have been updated with the given
			//command line arguments
			System.out.println("Command: " + argsBean.getCommand());
			System.out.println("Server: " + argsBean.getServer());
			System.out.println("Specified peer: " + argsBean.getSpecifiedPeer());
			
		} catch (CmdLineException e) {
			
			System.err.println(e.getMessage());
			
			//Print the usage to help the user understand the arguments expected
			//by the program
			parser.printUsage(System.err);
		}
	}
}

