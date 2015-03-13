package examples.als;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Array;

import parallel.Gadget;
import parallel.GatherFromEdges;
import parallel.GraphNode;
import parallel.Machine;
import parallel.ScatterToEdges;
import parallel.SortGadget;
import util.Utils;
import circuits.arithmetic.ArithmeticLib;
import circuits.arithmetic.DenseMatrixLib;
import circuits.arithmetic.FixedPointLib;
import circuits.arithmetic.IntegerLib;
import flexsc.CompEnv;
import flexsc.Party;
import gc.BadLabelException;

public class Als<T> extends Gadget<T> {

	public Als(CompEnv<T> env, Machine machine) {
		super(env, machine);
	}

	public static int ITERATIONS = 1;
	public static double GAMMA = 0.0002;
	public static double MU = 0.02;
	public static int USERS = 0;
	public static int ITEMS = 0;
	public static final int D = 10;

	private <T> AlsNode<T>[] readInput(int inputLength,
			CompEnv<T> env,
			int garblerId,
			int processors) throws IOException {
		boolean[][] u = null;
		boolean[][] v = null;
		boolean[] isV = null;
		boolean[][] rating = null;
		boolean[][][] userProfile = null;
		boolean[][][] itemProfile = null;
		boolean[] isUser = null;
		boolean[] isItem = null;
		if (env.getParty().equals(Party.Alice)) {
			u = new boolean[inputLength][GraphNode.VERTEX_LEN];
			v = new boolean[inputLength][GraphNode.VERTEX_LEN];
			isV = new boolean[inputLength];
			rating = new boolean[inputLength][AlsNode.FIX_POINT_WIDTH];
			userProfile = new boolean[inputLength][D][AlsNode.FIX_POINT_WIDTH];
			itemProfile = new boolean[inputLength][D][AlsNode.FIX_POINT_WIDTH];
			isUser = new boolean[inputLength];
			isItem = new boolean[inputLength];
		} else {
			Utils.RAND = new double[Utils.RAND_LIM];
			BufferedReader reader = new BufferedReader(new FileReader("in/rand.out"));
			for (int i21 = 0; i21 < Utils.RAND_LIM; i21++) {
				Utils.RAND[i21] = Double.parseDouble(reader.readLine());
			}
			reader.close();
			u = new boolean[inputLength][GraphNode.VERTEX_LEN];
			v = new boolean[inputLength][GraphNode.VERTEX_LEN];
			isV = new boolean[inputLength];
			rating = new boolean[inputLength][AlsNode.FIX_POINT_WIDTH];
			userProfile = new boolean[inputLength][D][AlsNode.FIX_POINT_WIDTH];
			itemProfile = new boolean[inputLength][D][AlsNode.FIX_POINT_WIDTH];
			isUser= new boolean[inputLength];
			isItem = new boolean[inputLength];
			BufferedReader br = new BufferedReader(new FileReader("in/als" + inputLength * processors + ".in"));
			USERS = Integer.parseInt(br.readLine());
			ITEMS = Integer.parseInt(br.readLine());
			int newIt = 0;
			for (int i11 = 0; i11 < inputLength * processors; i11++) {
				String readLine = br.readLine();
				if (!(i11 >= garblerId * inputLength && i11 < (garblerId + 1) * inputLength)) {
					continue;
				}
				String[] split = readLine.split(" ");
				u[newIt] = Utils.fromInt(Integer.parseInt(split[0]), GraphNode.VERTEX_LEN);
				v[newIt] = Utils.fromInt(Integer.parseInt(split[1]), GraphNode.VERTEX_LEN);
				isV[newIt] = (Integer.parseInt(split[2]) == 1);
				rating[newIt] = Utils.fromFixPoint(Double.parseDouble(split[3]), AlsNode.FIX_POINT_WIDTH, AlsNode.OFFSET);
				for (int j11 = 0; j11 < D; j11++) {
					userProfile[newIt][j11] = Utils.fromFixPoint(Utils.getRandom(), AlsNode.FIX_POINT_WIDTH, AlsNode.OFFSET);
				}
				for (int j31 = 0; j31 < D; j31++) {
					itemProfile[newIt][j31] = Utils.fromFixPoint(Utils.getRandom(), AlsNode.FIX_POINT_WIDTH, AlsNode.OFFSET);
				}
				if (newIt < USERS) {
					isUser[newIt] = true;
					isItem[newIt] = false;
				} else if (newIt < USERS + ITEMS) {
					isUser[newIt] = false;
					isItem[newIt] = true;
				} else {
					isUser[newIt] = false;
					isItem[newIt] = false;
				}
				newIt++;
			}
			br.close();
		}
		T[][] tu = (T[][]) env.inputOfBob(u);
		T[][] tv = (T[][]) env.inputOfBob(v);
		T[] tIsV = (T[]) env.inputOfBob(isV);
		T[][] tRating = (T[][]) env.inputOfBob(rating);
		T[][][] tUserProfile = (T[][][]) env.inputOfBob(userProfile);
		T[][][] tItemProfile = (T[][][]) env.inputOfBob(itemProfile);
		T[] tIsUser = (T[]) env.inputOfBob(isUser);
		T[] tIsItem = (T[]) env.inputOfBob(isItem);
		AlsNode<T>[] nodes = (AlsNode<T>[]) Array.newInstance(AlsNode.class, u.length);
		for (int i = 0; i < nodes.length; i++) {
			nodes[i] = new AlsNode<T>(tu[i], tv[i], tIsV[i], tRating[i], tUserProfile[i], tItemProfile[i], tIsUser[i], tIsItem[i], env);
		}
	    return nodes;
	}

	@Override
	public Object secureCompute() throws Exception {
		final IntegerLib<T> lib = new IntegerLib<>(env);
		final FixedPointLib<T> flib = new FixedPointLib<>(env, AlsNode.FIX_POINT_WIDTH, AlsNode.OFFSET);

		AlsNode<T>[] aa = readInput(machine.getInputLength() / machine.getTotalMachines(),
				env,
				machine.getGarblerId(),
				machine.getTotalMachines());
		
		new ScatterToEdges<T>(env, machine, false /* isEdgeIncoming */) {

			@Override
			public void writeToEdge(GraphNode<T> vertexNode,
					GraphNode<T> edgeNode, T isVertex) {
				AlsNode<T> vertex = (AlsNode<T>) vertexNode;
				AlsNode<T> edge = (AlsNode<T>) edgeNode;
				edge.up = lib.mux(vertex.up, edge.up, isVertex);
			}
		}.setInputs(aa).secureCompute();

		for (int it = 0; it < ITERATIONS; it++) {
			new ScatterToEdges<T>(env, machine, true /* isEdgeIncoming */) {

				@Override
				public void writeToEdge(GraphNode<T> vertexNode,
						GraphNode<T> edgeNode, T isVertex) {
					AlsNode<T> vertex = (AlsNode<T>) vertexNode;
					AlsNode<T> edge = (AlsNode<T>) edgeNode;
					edge.vp = lib.mux(vertex.vp, edge.vp, isVertex);
				}
			}.setInputs(aa).secureCompute();

			// compute values for up assuming vp; edge values
			for (int i = 0; i < aa.length; i++) {
				aa[i].solveU(env);
			}

			// gather values for user profiles
			new GatherFromEdges<T>(env, machine, false /* isEdgeIncoming */, new AlsNode<>(env)) {

				@Override
				public GraphNode<T> aggFunc(GraphNode<T> aggNode, GraphNode<T> bNode)
						throws IOException {
					AlsNode<T> agg = (AlsNode<T>) aggNode;
					AlsNode<T> b = (AlsNode<T>) bNode;
					AlsNode<T> ret = new AlsNode<>(env);
					for (int i = 0; i < D; i++) {
						ret.vp[i] = flib.add(agg.vp[i], b.vp[i]);
						for (int j = 0; j < D; j++) {
							ret.M[i][j] = flib.add(agg.M[i][j], b.M[i][j]);
						}
					}
					return ret;
				}

				@Override
				public void writeToVertex(GraphNode<T> aggNode, GraphNode<T> bNode) {
					AlsNode<T> agg = (AlsNode<T>) aggNode;
					AlsNode<T> vertex = (AlsNode<T>) bNode;
					vertex.vp = lib.mux(vertex.vp, agg.vp, vertex.isU);
					vertex.M = lib.mux(vertex.M, agg.M, vertex.isU);
					for (int i = 0; i < D; i++) {
						vertex.M[i][i] = flib.add(vertex.M[i][i], flib.publicValue(MU));
					}
				}
			}.setInputs(aa).secureCompute();

			// compute P-1 A
			for (int i = 0; i < aa.length; i++) {
				T[][][] rref = getRowReducedMatrix(env, aa, i, true /* isItem */, flib);
				for (int j = 0; j < D; j++) {
					aa[i].up[j] = lib.mux(aa[i].up[j], rref[j][D], aa[i].isU);
				}
			}

			// scatter user profiles
			new ScatterToEdges<T>(env, machine, false /* isEdgeIncoming */) {

				@Override
				public void writeToEdge(GraphNode<T> vertexNode,
						GraphNode<T> edgeNode, T isVertex) {
					AlsNode<T> vertex = (AlsNode<T>) vertexNode;
					AlsNode<T> edge = (AlsNode<T>) edgeNode;
					edge.up = lib.mux(vertex.up, edge.up, isVertex);
				}
			}.setInputs(aa).secureCompute();

			// compute values for vp assuming up; edge values
			for (int i = 0; i < aa.length; i++) {
				aa[i].solveV(env);
			}

			// gather values for item profiles
			new GatherFromEdges<T>(env, machine, true /* isEdgeIncoming */, new AlsNode<>(env)) {

				@Override
				public GraphNode<T> aggFunc(GraphNode<T> aggNode, GraphNode<T> bNode)
						throws IOException {
					AlsNode<T> agg = (AlsNode<T>) aggNode;
					AlsNode<T> b = (AlsNode<T>) bNode;
					AlsNode<T> ret = new AlsNode<>(env);
					for (int i = 0; i < D; i++) {
						ret.up[i] = flib.add(agg.up[i], b.up[i]);
						for (int j = 0; j < D; j++) {
							ret.M[i][j] = flib.add(agg.M[i][j], b.M[i][j]);
						}
					}
					return ret;
				}

				@Override
				public void writeToVertex(GraphNode<T> aggNode, GraphNode<T> bNode) {
					AlsNode<T> agg = (AlsNode<T>) aggNode;
					AlsNode<T> vertex = (AlsNode<T>) bNode;
					vertex.up = lib.mux(vertex.up, agg.up, vertex.isV);
					vertex.M = lib.mux(vertex.M, agg.M, vertex.isV);
					for (int i = 0; i < D; i++) {
						vertex.M[i][i] = flib.add(vertex.M[i][i], flib.publicValue(MU));
					}
				}
			}.setInputs(aa).secureCompute();

			// compute P-1 A for items
			for (int i = 0; i < aa.length; i++) {
				T[][][] rref = getRowReducedMatrix(env, aa, i, false /* isItem */, flib);
				for (int j = 0; j < D; j++) {
					aa[i].vp[j] = lib.mux(aa[i].vp[j], rref[j][D], aa[i].isV);
				}
			}
		}
		new SortGadget<>(env, machine).setInputs(aa, GraphNode.allVerticesFirst(env)).secureCompute();
		print(machine.getGarblerId(), env, aa);
		return null;
	}

	private <T> T[][][] getRowReducedMatrix(CompEnv<T> env, AlsNode<T>[] aa, int i, boolean isItem, ArithmeticLib<T> flib) {
		T[][][] inp = env.newTArray(D, D + 1, AlsNode.FIX_POINT_WIDTH);
		for (int j = 0; j < D; j++) {
			for (int k = 0; k < D; k++) {
				inp[j][k] = aa[i].M[j][k];
			}
			if (isItem) {
				inp[j][D] = aa[i].vp[j];
			} else {
				inp[j][D] = aa[i].up[j];
			}
		}
		DenseMatrixLib<T> dmLib = new DenseMatrixLib<>(env, flib);
		T[][][] rref = dmLib.rref(inp);
		return rref;
	}

	private <T> void print(int machineId, final CompEnv<T> env, AlsNode<T>[] alsNode) throws IOException, BadLabelException, InterruptedException {
		FixedPointLib<T> lib = new FixedPointLib<>(env, AlsNode.FIX_POINT_WIDTH, AlsNode.OFFSET);
		if (machineId == 1) {
			Thread.sleep(1000);
		} else {
			System.out.println();
		}
		for (int i = 0; i < alsNode.length; i++) {
			int a = Utils.toInt(env.outputToAlice(alsNode[i].u));
			int b = Utils.toInt(env.outputToAlice(alsNode[i].v));
			double r = lib.outputToAlice(alsNode[i].rating);
			double c2 = lib.outputToAlice(alsNode[i].up[0]);
			double c3 = lib.outputToAlice(alsNode[i].up[1]);
			double d = lib.outputToAlice(alsNode[i].vp[0]);
			double d2 = lib.outputToAlice(alsNode[i].vp[1]);
			boolean isU = env.outputToAlice(alsNode[i].isU);
			boolean isV = env.outputToAlice(alsNode[i].isV);
			env.channel.flush();
			if (Party.Alice.equals(env.party)) {
				System.out.format("%d: %d, %d \t %.2f \t %.4f \t %.4f \t %.4f \t %.4f \t %b \t %b\n", machineId, a, b, r, c2, c3, d, d2, isU, isV);
			}
	    }
	}
}

