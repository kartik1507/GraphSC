package examples.pr;

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
import util.Constants;
import util.Utils;
import circuits.arithmetic.ArithmeticLib;
import circuits.arithmetic.FixedPointLib;
import circuits.arithmetic.IntegerLib;
import flexsc.CompEnv;
import flexsc.Party;
import gc.BadLabelException;

public class PageRank<T> extends Gadget<T> {
	static int ITERATIONS = 1;

	public PageRank(CompEnv<T> env, Machine machine) {
		super(env, machine);
	}

	private Object[] getInput(int inputLength, int garblerId, int processors) throws IOException {
		int[] u = new int[inputLength];
		int[] v = new int[inputLength];
		boolean[] isVertex = new boolean[inputLength];
		BufferedReader br = new BufferedReader(new FileReader(Constants.INPUT_DIR + "PageRank" + inputLength * processors + ".in"));
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
		boolean[][] a = new boolean[u.length][];
		boolean[][] b = new boolean[v.length][];
		boolean[] c = new boolean[isVertex.length];
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
		PageRankNode<T>[] nodes = (PageRankNode<T>[]) Array.newInstance(PageRankNode.class, u.length);
		for (int i = 0; i < nodes.length; i++) {
			nodes[i] = new PageRankNode<T>(tu[i], tv[i], tIsV[i], env);
		}
		
		
		
		
		
		
		// business logic
		final IntegerLib<T> lib = new IntegerLib<>(env);
		final ArithmeticLib<T> flib = new FixedPointLib<T>(env, PageRankNode.WIDTH, PageRankNode.OFFSET);

		// set initial pagerank
		new SetInitialPageRankGadget<T>(env, machine)
				.setInputs(nodes)
				.secureCompute();

		// 1. Compute number of neighbors for each vertex
		new GatherFromEdges<T>(env, machine, false /* isEdgeIncoming */, new PageRankNode<T>(env)) {

			@Override
			public GraphNode<T> aggFunc(GraphNode<T> aggNode, GraphNode<T> bNode) {
				PageRankNode<T> agg = (PageRankNode<T>) aggNode;
				PageRankNode<T> b = (PageRankNode<T>) bNode;
				PageRankNode<T> ret = new PageRankNode<T>(env);
				ret.l = flib.add(agg.l, b.l);
				return ret;
			}

			@Override
			public void writeToVertex(GraphNode<T> aggNode, GraphNode<T> bNode) {
				PageRankNode<T> agg = (PageRankNode<T>) aggNode;
				PageRankNode<T> b = (PageRankNode<T>) bNode;
				b.l = lib.mux(b.l, agg.l, b.isVertex);
			}
		}.setInputs(nodes).secureCompute();

		for (int i = 0; i < ITERATIONS; i++) {
			// 2. Write weighted PR to edges
			new ScatterToEdges<T>(env, machine, false /* isEdgeIncoming */) {

				@Override
				public void writeToEdge(GraphNode<T> vertexNode,
						GraphNode<T> edgeNode, T cond) {
					PageRankNode<T> vertex = (PageRankNode<T>) vertexNode;
					PageRankNode<T> edge = (PageRankNode<T>) edgeNode;
					T[] div = flib.div(vertex.pr, vertex.l);
					edge.pr = lib.mux(div, edge.pr, cond);
				}
			}.setInputs(nodes).secureCompute();

			// 3. Compute PR based on edges
			new GatherFromEdges<T>(env, machine, true /* isEdgeIncoming */, new PageRankNode<T>(env)) {

				@Override
				public GraphNode<T> aggFunc(GraphNode<T> aggNode, GraphNode<T> bNode) {
					PageRankNode<T> agg = (PageRankNode<T>) aggNode;
					PageRankNode<T> b = (PageRankNode<T>) bNode;

					PageRankNode<T> ret = new PageRankNode<T>(env);
					ret.pr = flib.add(agg.pr, b.pr);
					return ret;
				}

				@Override
				public void writeToVertex(GraphNode<T> aggNode, GraphNode<T> bNode) {
					PageRankNode<T> agg = (PageRankNode<T>) aggNode;
					PageRankNode<T> b = (PageRankNode<T>) bNode;
					b.pr = lib.mux(b.pr, agg.pr, b.isVertex);
				}
			}.setInputs(nodes).secureCompute();
		}
		new SortGadget<T>(env, machine)
			.setInputs(nodes, PageRankNode.allVerticesFirst(env))
			.secureCompute();
		print(machine.getGarblerId(), env, nodes, ITERATIONS, flib);
		return null;
	}

	private <T> void print(int garblerId,
			final CompEnv<T> env,
			PageRankNode<T>[] pr,
			int iterations,
			ArithmeticLib<T> flib) throws IOException, BadLabelException {
		if (garblerId == 0 && Party.Alice.equals(env.getParty())) {
			System.out.println("PageRank of vertices after " + iterations + " iteration(s):");
		}
		for (int i = 0; i < pr.length; i++) {
			int u = Utils.toInt(env.outputToAlice(pr[i].u));
			double pageRank = flib.outputToAlice(pr[i].pr);
			boolean e = env.outputToAlice(pr[i].isVertex);
			env.channel.flush();
			if (Party.Alice.equals(env.party)) {
				if (e) {
					System.out.format("%d %.2f\n", u, pageRank);
				}
			}
	    }
	}
}