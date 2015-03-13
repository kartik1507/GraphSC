package examples.gd;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Array;

import parallel.Gadget;
import parallel.GatherFromEdges;
import parallel.GatherFromEdgesRight;
import parallel.GraphNode;
import parallel.Machine;
import parallel.ScatterToEdges;
import parallel.ScatterToEdgesRight;
import parallel.SortGadget;
import util.Constants;
import util.Utils;
import circuits.arithmetic.FixedPointLib;
import circuits.arithmetic.IntegerLib;
import flexsc.CompEnv;
import flexsc.Party;
import gc.BadLabelException;

public class MatrixFactorization<T> extends Gadget<T> {

	public MatrixFactorization(CompEnv<T> env, Machine machine) {
		super(env, machine);
	}

	public static int ITERATIONS = 1;
	public static double GAMMA = 0.0002;
	public static double LAMBDA = 0.02;
	public static double MU = 0.02;

	private Object[] getInput(int inputLength, int garblerId, int processors) throws IOException {
		// can be replaced by a regular random number generator
		Utils.generateRandomNumbers();
		BufferedReader br = new BufferedReader(new FileReader(Constants.INPUT_DIR + "mf" + inputLength * processors + ".in"));
		boolean[][] ua = new boolean[inputLength][];
		boolean[][] va = new boolean[inputLength][];
		boolean[] isVertexa = new boolean[inputLength];
		boolean[][] ratinga = new boolean[inputLength][];
		boolean[][][] userProfilea = new boolean[inputLength][MFNode.D][];
		boolean[][][] itemProfilea = new boolean[inputLength][MFNode.D][];
		int k = 0;
		for (int i = 0; i < inputLength * processors; i++) {
			String readLine = br.readLine();
			if (!(i >= garblerId * inputLength && i < (garblerId + 1) * inputLength)) {
				continue;
			}
			String[] split = readLine.split(" ");
			ua[k] = Utils.fromInt(Integer.parseInt(split[0]), GraphNode.VERTEX_LEN);
			va[k] = Utils.fromInt(Integer.parseInt(split[1]), GraphNode.VERTEX_LEN);
			isVertexa[k] = (Integer.parseInt(split[2]) == 1);
			ratinga[k] = Utils.fromFixPoint(Double.parseDouble(split[3]), MFNode.FIX_POINT_WIDTH, MFNode.OFFSET);
			for (int j = 0; j < MFNode.D; j++) {
				userProfilea[k][j] = Utils.fromFixPoint(Utils.getRandom(), MFNode.FIX_POINT_WIDTH, MFNode.OFFSET);
			}
			for (int j = 0; j < MFNode.D; j++) {
				itemProfilea[k][j] = Utils.fromFixPoint(Utils.getRandom(), MFNode.FIX_POINT_WIDTH, MFNode.OFFSET);
			}
			k++;
		}
		br.close();
		Object[] ret = new Object[6];
		ret[0] = ua;
		ret[1] = va;
		ret[2] = isVertexa;
		ret[3] = ratinga;
		ret[4] = userProfilea;
		ret[5] = itemProfilea;
		return ret;
	}


	@Override
	public Object secureCompute() throws Exception {
		// prepare data
		int inputLength = machine.getInputLength() / machine.getTotalMachines();
		boolean[][] u = null;
		boolean[][] v = null;
		boolean[] isV = null;
		boolean[][] rating = null;
		boolean[][][] userProfile = null;
		boolean[][][] itemProfile = null;
		if (env.getParty().equals(Party.Alice)) {
			u = new boolean[inputLength][GraphNode.VERTEX_LEN];
			v = new boolean[inputLength][GraphNode.VERTEX_LEN];
			isV = new boolean[inputLength];
			rating = new boolean[inputLength][MFNode.FIX_POINT_WIDTH];
			userProfile = new boolean[inputLength][MFNode.D][MFNode.FIX_POINT_WIDTH];
			itemProfile = new boolean[inputLength][MFNode.D][MFNode.FIX_POINT_WIDTH];
		} else {
			Object[] input = getInput(inputLength, machine.getGarblerId(), machine.getTotalMachines());
			u = (boolean[][]) input[0];
			v = (boolean[][]) input[1];
			isV = (boolean[]) input[2];
			rating = (boolean[][]) input[3];
			userProfile = (boolean[][][]) input[4];
			itemProfile = (boolean[][][]) input[5];
		}
		T[][] tu = (T[][]) env.inputOfBob(u);
		T[][] tv = (T[][]) env.inputOfBob(v);
		T[] tIsV = (T[]) env.inputOfBob(isV);
		T[][] tRating = (T[][]) env.inputOfBob(rating);
		T[][][] tUserProfile = (T[][][]) env.inputOfBob(userProfile);
		T[][][] tItemProfile = (T[][][]) env.inputOfBob(itemProfile);
		MFNode<T>[] nodes = (MFNode<T>[]) Array.newInstance(MFNode.class, u.length);
		for (int i = 0; i < nodes.length; i++) {
			nodes[i] = new MFNode<T>(tu[i], tv[i], tIsV[i], tRating[i], tUserProfile[i], tItemProfile[i], env);
		}


		// business logic




		final FixedPointLib<T> fixedPointLib = new FixedPointLib<T>(env,
				MFNode.FIX_POINT_WIDTH,
				MFNode.OFFSET);
		final IntegerLib<T> lib = new IntegerLib<>(env);

		final T[] twoGammaLambda = fixedPointLib.publicValue(2 * MatrixFactorization.GAMMA * MatrixFactorization.LAMBDA);

		new SortGadget<T>(env, machine)
				.setInputs(nodes, nodes[0].getComparator(env, true /* isVertexLast */))
				.secureCompute();
		for (int it = 0; it < ITERATIONS; it++) {
			// scatter user profiles
			new ScatterToEdgesRight<T>(env, machine, false /* isEdgeIncoming */) {
	
				@Override
				public void writeToEdge(GraphNode<T> vertexNode,
						GraphNode<T> edgeNode, T isVertex) {
					MFNode<T> vertex = (MFNode<T>) vertexNode;
					MFNode<T> edge = (MFNode<T>) edgeNode;
					edge.userProfile = lib.mux(vertex.userProfile, edge.userProfile, isVertex);
				}
			}.setInputs(nodes).secureCompute();

			// scatter item profiles
			new ScatterToEdges<T>(env, machine, true /* isEdgeIncoming */) {
	
				@Override
				public void writeToEdge(GraphNode<T> vertexNode,
						GraphNode<T> edgeNode, T isVertex) {
					MFNode<T> vertex = (MFNode<T>) vertexNode;
					MFNode<T> edge = (MFNode<T>) edgeNode;
					edge.itemProfile = lib.mux(vertex.itemProfile, edge.itemProfile, isVertex);
				}
			}.setInputs(nodes).secureCompute();

			// compute gradient
			new ComputeGradient<T>(env, machine)
				.setInputs(nodes)
				.secureCompute();

			// update item profiles
			new GatherFromEdgesRight<T>(env, machine, true /* isEdgeIncoming */, new MFNode<>(env)) {
	
				@Override
				public GraphNode<T> aggFunc(GraphNode<T> aggNode, GraphNode<T> bNode) {
					MFNode<T> agg = (MFNode<T>) aggNode;
					MFNode<T> b = (MFNode<T>) bNode;
					MFNode<T> ret = new MFNode<>(env);
					for (int i = 0; i < ret.itemProfile.length; i++) {
						ret.itemProfile[i] = fixedPointLib.add(agg.itemProfile[i], b.itemProfile[i]);
					}
					return ret;
				}

				@Override
				public void writeToVertex(GraphNode<T> aggNode, GraphNode<T> bNode) {
					MFNode<T> agg = (MFNode<T>) aggNode;
					MFNode<T> b = (MFNode<T>) bNode;
					for (int i = 0; i < agg.itemProfile.length; i++) {
						T[] edgeNodeAgg = fixedPointLib.add(agg.itemProfile[i], b.itemProfile[i]);
						T[] reg = fixedPointLib.multiply(twoGammaLambda, b.itemProfile[i]);
						T[] total = fixedPointLib.add(edgeNodeAgg, reg);
						b.itemProfile[i] = lib.mux(b.itemProfile[i], total, b.isVertex);
					}
				}
			}.setInputs(nodes).secureCompute();

			// update user profiles
			new GatherFromEdges<T>(env, machine, false /* isEdgeIncoming */, new MFNode<>(env)) {
	
				@Override
				public GraphNode<T> aggFunc(GraphNode<T> aggNode, GraphNode<T> bNode) {
					MFNode<T> agg = (MFNode<T>) aggNode;
					MFNode<T> b = (MFNode<T>) bNode;
					MFNode<T> ret = new MFNode<>(env);
					for (int i = 0; i < ret.userProfile.length; i++) {
						ret.userProfile[i] = fixedPointLib.add(agg.userProfile[i], b.userProfile[i]);
					}
					return ret;
				}
	
				@Override
				public void writeToVertex(GraphNode<T> aggNode, GraphNode<T> bNode) {
					MFNode<T> agg = (MFNode<T>) aggNode;
					MFNode<T> b = (MFNode<T>) bNode;
					for (int i = 0; i < agg.userProfile.length; i++) {
						T[] edgeNodeAgg = fixedPointLib.add(agg.userProfile[i], b.userProfile[i]);
						T[] reg = fixedPointLib.multiply(twoGammaLambda, b.userProfile[i]);
						T[] total = fixedPointLib.add(edgeNodeAgg, reg);
						b.userProfile[i] = lib.mux(b.userProfile[i], total, b.isVertex);
					}
				}
			}.setInputs(nodes).secureCompute();
		}

		new SortGadget<>(env, machine)
			.setInputs(nodes, GraphNode.allVerticesFirst(env))
			.secureCompute();
		print(machine.getGarblerId(), env, nodes);
		return null;
	}

	private <T> void print(int machineId, final CompEnv<T> env, MFNode<T>[] mfNode) throws IOException, BadLabelException, InterruptedException {
		FixedPointLib<T> lib = new FixedPointLib<>(env, MFNode.FIX_POINT_WIDTH, MFNode.OFFSET);
		if (machineId == 1) {
			Thread.sleep(1000);
		} else {
			System.out.println();
		}
		for (int i = 0; i < mfNode.length; i++) {
			int a = Utils.toInt(env.outputToAlice(mfNode[i].u));
			int b = Utils.toInt(env.outputToAlice(mfNode[i].v));
			double r = lib.outputToAlice(mfNode[i].rating);
			double c2 = lib.outputToAlice(mfNode[i].userProfile[0]);
			double c3 = lib.outputToAlice(mfNode[i].userProfile[1]);
			double d = lib.outputToAlice(mfNode[i].itemProfile[0]);
			double d2 = lib.outputToAlice(mfNode[i].itemProfile[1]);
			env.channel.flush();
			if (Party.Alice.equals(env.party)) {
				System.out.format("%d: %d, %d \t %.2f \t %.6f \t %.6f \t %.6f \t %.6f\n", machineId, a, b, r, c2, c3, d, d2);
			}
	    }
	}
}
