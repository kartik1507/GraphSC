package parallel;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Hashtable;

import util.Constants;

public class IPManager {

	static String[] gIp;
	static String[] eIp;
	int machines;
	static Hashtable<String, String> machineIps;

	public static IPManager loadIPs(int machines, String machineConfigFile) throws IOException {
		IPManager ipManager = new IPManager();
		IPManager.machineIps = new Hashtable<>();
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(Constants.MACHINE_SPEC + machineConfigFile + "." + machines));
			String next = "";
			while (true) {
				next = checkComment(br, next);
				if (next.startsWith("processor_spec")) {
					break;
				}
				machineIps.put(next.split(" ")[0], next.split(" ")[1]);
			}
			next = checkComment(br, next);
			setGarblerEvaluatorIps(machines, next);
			next = checkComment(br, next);
			setGarblerEvaluatorIps(machines, next);
			ipManager.machines = machines;
			br.close();
			return ipManager;
		} catch(FileNotFoundException e) {
			System.out.println("Please store machine specification in " + e.getMessage());
		}
		return null;
	}

	private static String checkComment(BufferedReader br, String next)
			throws IOException {
		next = br.readLine();
		while (next.startsWith("#")) {
			next = br.readLine();
		}
		return next;
	}

	private static void setGarblerEvaluatorIps(int machines, String next) {
		if (next.startsWith("garbler")) {
			String[] split = next.split(" ");
			if (split.length != machines + 1) {
				System.out.println("You have specified " + (split.length - 1) + "  garblers instead of " + machines);
			}
			gIp = new String[machines];
			for (int i = 1; i < split.length; i++) {
				gIp[i - 1] = machineIps.get(split[i]);
			}
		} else if (next.startsWith("evaluator")) {
			String[] split = next.split(" ");
			if (split.length != machines + 1) {
				System.out.println("You have specified " + (split.length - 1) + "  evaluators instead of " + machines);
			}
			eIp = new String[machines];
			for (int i = 1; i < split.length; i++) {
				eIp[i - 1] = machineIps.get(split[i]);
			}
		}
	}
}
