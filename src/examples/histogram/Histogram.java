package examples.histogram;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Array;

import parallel.Gadget;
import parallel.GatherFromEdges;
import parallel.GraphNode;
import parallel.Machine;
import parallel.SortGadget;
import util.Constants;
import util.Utils;
import circuits.arithmetic.IntegerLib;
import flexsc.CompEnv;
import flexsc.Party;

public class Histogram<T> extends Gadget<T> {

	public Histogram(CompEnv<T> env, Machine machine) {
		super(env, machine);
	}

	private Object[] getInput(int inputLength, int garblerId, int processors) throws IOException {
		int[] u = new int[inputLength];
		int[] v = new int[inputLength];
		boolean[] isVertex = new boolean[inputLength];
		boolean[][] a = new boolean[u.length][];
		boolean[][] b = new boolean[v.length][];
		boolean[] c = new boolean[isVertex.length];
		BufferedReader br = new BufferedReader(new FileReader(
				Constants.INPUT_DIR + "Histogram" + inputLength * processors + ".in"));
		int j = 0;
		for (int i = 0; i < inputLength * processors; i++) {
			String readLine = br.readLine();
			if (!(i >= garblerId * inputLength && i < (garblerId + 1) * inputLength)) {
				continue;
			}
			String[] split = readLine.split(" ");
			u[j] = Integer.parseInt(split[0]);
			v[j] = Integer.parseInt(split[1]);
			isVertex[j] = (Integer.parseInt(split[2]) == 1);
			j++;
		}
		br.close();
		for(int i = 0; i < u.length; ++i) {
			a[i] = Utils.fromInt(u[i], GraphNode.VERTEX_LEN);
			b[i] = Utils.fromInt(v[i], GraphNode.VERTEX_LEN);
			c[i] = isVertex[i];
		}
		Object[] ret = new Object[3];
		ret[0] = a;
		ret[1] = b;
		ret[2] = c;
		return ret;
	}

	@Override
	public Object secureCompute() throws Exception {
		int inputLength = machine.getInputLength() / machine.getTotalMachines();
		boolean[][] u = null;
		boolean[][] v = null;
		boolean[] isV = null;
		if (env.getParty().equals(Party.Alice)) {
			u = new boolean[inputLength][GraphNode.VERTEX_LEN];
			v = new boolean[inputLength][GraphNode.VERTEX_LEN];
			isV = new boolean[inputLength];
		} else {
			Object[] input = getInput(inputLength, machine.getGarblerId(), machine.getTotalMachines());
			u = (boolean[][]) input[0];
			v = (boolean[][]) input[1];
			isV = (boolean[]) input[2];
		}
		T[][] tu = (T[][]) env.inputOfBob(u);
		T[][] tv = (T[][]) env.inputOfBob(v);
		T[] tIsV = (T[]) env.inputOfBob(isV);
		HistogramNode<T>[] nodes = (HistogramNode<T>[]) Array.newInstance(HistogramNode.class, u.length);
		for (int i = 0; i < nodes.length; i++) {
			nodes[i] = new HistogramNode<T>(tu[i], tv[i], tIsV[i], env);
		}
		
		
		
		
		
		// business logic
		final IntegerLib<T> lib = new IntegerLib<>(env);

		new GatherFromEdges<T>(env, machine, true /* isEdgeIncoming */, new HistogramNode<>(env)) {

			@Override
			public GraphNode<T> aggFunc(GraphNode<T> agg, GraphNode<T> b) throws IOException {
				HistogramNode<T> ret = new HistogramNode<T>(env);
				ret.count = lib.add(((HistogramNode<T>) agg).count, ((HistogramNode<T>) b).count);
				return ret;
			}

			@Override
			public void writeToVertex(GraphNode<T> aggNode, GraphNode<T> bNode) {
				HistogramNode<T> agg = (HistogramNode<T>) aggNode;
				HistogramNode<T> b = (HistogramNode<T>) bNode;
				b.count = lib.mux(b.count, agg.count, b.isVertex);
			}
		}.setInputs(nodes).secureCompute();

		new SortGadget<T>(env, machine)
			.setInputs(nodes, GraphNode.allVerticesFirst(env))
			.secureCompute();

		for (int i = 0; i < 4; i++) {
			int int2 = Utils.toInt(env.outputToAlice(nodes[i].v));
			int int3 = Utils.toInt(env.outputToAlice(nodes[i].count));
			if (Party.Alice.equals(env.party) && machine.getGarblerId() == 0) {
				System.out.println(machine.getGarblerId() + ": " + int2 + ", " + int3);
			}
		}
		return null;
	}
}
