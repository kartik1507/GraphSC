package parallel;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import network.Client;
import network.Network;
import network.Server;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import util.Constants;
import util.Utils;
import flexsc.CompEnv;
import flexsc.Flag;
import flexsc.Mode;
import flexsc.Party;
import gc.GCGenComp;
import gc.GCSignal;
import gc.offline.FileReader;
import gc.offline.GCGen;

public class Machine {
	public static boolean DEBUG = false;

	private int garblerId;
	private int peerPort;
	private int totalMachines;
	private int logMachines;
	private boolean isGen;
	private int inputLength;
	private Gadget gadget;
	private CompEnv env;
	Network[] peersUp;
	Network[] peersDown;

	int numberOfIncomingConnections;
	int numberOfOutgoingConnections;

	public Machine(int garblerId,
			int totalMachines,
			boolean isGen,
			int inputLength,
			int peerPort) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		this.garblerId = garblerId;
		this.totalMachines = totalMachines;
		this.isGen = isGen;
		this.inputLength = inputLength;
		this.peerPort = peerPort;
		this.logMachines = Utils.log2(this.totalMachines);
		if (logMachines > 0) {
			this.peersUp = new Network[logMachines];
			this.peersDown = new Network[logMachines];
		}
		this.numberOfIncomingConnections = this.getNumberOfIncomingConnections(this.garblerId);
		this.numberOfOutgoingConnections = this.getNumberOfIncomingConnections(totalMachines - this.garblerId - 1);
	}

	void connect() throws InterruptedException, IOException, ClassNotFoundException {
		// TODO(kartiknayak): This may necessitate 2^x input length
        listenFromPeer(peerPort + garblerId);
        connectToPeers();
        if (garblerId == 0) {
        	setRInFile();
        }
        if (garblerId > 0) {
        	GCGenComp.R = GCSignal.receive(peersUp[0]);
        	setRInFile();
        }
        if (garblerId < totalMachines - 1) {
        	GCGenComp.R.send(peersDown[0]);
        	peersDown[0].flush();
        }
//        debug(" " + env.getParty() + " connected?");
	}

	private void setRInFile() {
		if (isGen && env.getMode().equals(Mode.OFFLINE)) {
			try {
				if(Flag.offline) {
					GCGen.fread = new FileReader(Flag.tableName + garblerId);
					GCGenComp.R = new GCSignal(GCGen.fread.read(10));
					GCGenComp.R.setLSB();
				}
				else {
					GCGen.fout = new BufferedOutputStream(new FileOutputStream(Flag.tableName + garblerId),
							Constants.OFFLINE_FILE_BUFFER_SIZE);
					GCGenComp.R.send(GCGen.fout);
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
	}

	public void setMachineId(int machineId, int peerPort, int totalMachines) {
		this.totalMachines = totalMachines;
		this.logMachines = Utils.log2(totalMachines);
		this.garblerId = machineId;
		this.numberOfIncomingConnections = getNumberOfIncomingConnections(machineId);
		this.numberOfOutgoingConnections = getNumberOfIncomingConnections(totalMachines - machineId - 1);
		this.peerPort = peerPort;
	}

	public void listenFromPeer(int port) throws IOException {

		Socket clientSock = null;
		ServerSocket serverSocket = new ServerSocket(port);
        for (int i = 0; i < numberOfIncomingConnections; i++) {
        	clientSock = serverSocket.accept();
        	OutputStream os = new BufferedOutputStream(clientSock.getOutputStream());
        	InputStream     is = new BufferedInputStream(clientSock.getInputStream());
        	Network channel = new Network(is, os, clientSock);
        	int id = channel.readInt();
			int index = Utils.log2(id - garblerId);
			debug("Accepted a connection from " + id + ". Stored at index " + index);
			peersDown[index] = channel;
			debug(id + " peerIsDown " + peersDown[index].hashCode());
        }
        serverSocket.close();
	}

	public void connectToPeers() throws InterruptedException, IOException {
		for (int i = 0; i < numberOfOutgoingConnections; i++) {
			debug("I'm trying to connect to " + (garblerId - (1 << i)) + " at " + (peerPort + garblerId - (1 << i)) + ". Storing connection at " + i);
			String peerIp = null;
			peerIp = isGen ? IPManager.gIp[(garblerId - (1 << i))] : IPManager.eIp[(garblerId - (1 << i))];
			peersUp[i] = new Client();
			((Client) peersUp[i]).connect(peerIp, peerPort + garblerId - (1 << i));
			peersUp[i].writeInt(garblerId);
			peersUp[i].flush();
			debug((garblerId - (1 << i)) + "peerOsUp " + peersUp[i].hashCode());
		}
		debug("I'm done connecting ");
	}

	CompEnv connectToOtherParty(Mode mode, int compPoolGenEvaPort) 
			throws InterruptedException, IOException, ClassNotFoundException {
		Party party = isGen ? Party.Alice : Party.Bob;
		Network channel = null;
		if (isGen) {
			channel = new Server();
			((Server) channel).listen(compPoolGenEvaPort);
		} else {
			channel = new Client();
			// my evaluator's machine id is going to be the same as mine
			((Client) channel).connect(IPManager.gIp[garblerId], compPoolGenEvaPort);
		}
		debug(" " + party + " connected to other party");
		return CompEnv.getEnv(mode, party, channel);
	}

	static CommandLine processArgs(String[] args) throws ParseException {
		Options options = new Options();
		options.addOption("pid", "garblerId", true /* hasArg */, "machineId");
		options.addOption("port", "garblerPort", true, "garblePort");
		options.addOption("g", "isGen", true, "isGen");
		options.addOption("n", "inputLength", true, "inputLength");
		options.addOption("program", "program", true, "program");
		options.addOption("p", "totalGarblers", true, "totalGarblers");
		options.addOption("machineConfigFile", "machineConfigFile", true, "machineConfigFile");
		options.addOption("m", "mode", true, "Mode");
		options.addOption("peer", "peerBasePort", true, "Peer base port");
		options.addOption("offline", "offline", true, "Whether it is offline; only applicable to offline mode");

		CommandLineParser parser = new BasicParser();
		CommandLine cmd = parser.parse(options, args);

		return cmd;
	}

	public static <T> void main(String args[]) throws Exception {
		CommandLine cmd = processArgs(args);
		int machines = Integer.parseInt(cmd.getOptionValue("totalGarblers"));
		String machineConfigFile = cmd.getOptionValue("machineConfigFile");
		IPManager.loadIPs(machines, machineConfigFile);
		int compPoolGenEvaPort = Integer.parseInt(cmd.getOptionValue("garblerPort"));
		Mode mode = Mode.valueOf(cmd.getOptionValue("mode"));
		Machine machine = new Machine(Integer.parseInt(cmd.getOptionValue("garblerId")) /* garblerId */,
				Integer.parseInt(cmd.getOptionValue("totalGarblers")) /* machines */,
				Boolean.parseBoolean(cmd.getOptionValue("isGen")) /* isGen */,
				Integer.parseInt(cmd.getOptionValue("inputLength")) /* inputLength */,
				Integer.parseInt(cmd.getOptionValue("peerBasePort")) /* peerPort */);

		// Connect to the other party
		machine.env = machine.connectToOtherParty(mode, compPoolGenEvaPort);
		if (machine.env.getMode().equals(Mode.OFFLINE)) {
			Flag.offline = Boolean.parseBoolean(cmd.getOptionValue("offline"));
		}
		machine.connect();
		String experiment = cmd.getOptionValue("program");
		Class c = Class.forName("examples." + experiment);
		machine.gadget = (Gadget) c.getConstructor(new Class[]{CompEnv.class, Machine.class})
				.newInstance(machine.env, machine);
		machine.gadget.secureCompute();
		machine.disconnect();

		if(!Flag.offline && machine.env.getParty().equals(Party.Alice)) {
			try {
				gc.offline.GCGen.fout.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void disconnect() throws IOException {
		disconnectFromPeers();
	}

	private void disconnectFromPeers() throws IOException {
		for (int i = 0; i < numberOfIncomingConnections; i++) {
			peersDown[i].disconnect();
		}
		for (int i = 0; i < numberOfOutgoingConnections; i++) {
			peersUp[i].disconnect();
		}
	}

	private int getNumberOfIncomingConnections(int machineId) {
		int k = 0;
		while (true) {
			if (machineId >= totalMachines - (1 << k)) {
				return k;
			}
			k++;
		}
	}

	protected void debug(String debug) {
		if (DEBUG) {
			System.out.println(getGarblerIdString() + ": " + debug);
		}
	}

	public String getGarblerIdString() {
		return (garblerId < 10) ? "0" + garblerId : "" + garblerId;
	}

	public int getGarblerId() {
		return garblerId;
	}

	public int getTotalMachines() {
		return totalMachines;
	}

	public int getLogMachines() {
		return logMachines;
	}

	public boolean isGen() {
		return isGen;
	}

	public int getInputLength() {
		return inputLength;
	}
}
