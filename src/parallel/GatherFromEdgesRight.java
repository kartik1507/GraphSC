package parallel;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import circuits.arithmetic.IntegerLib;
import flexsc.CompEnv;
import gc.BadLabelException;

public abstract class GatherFromEdgesRight<T> extends Gadget<T> {

	private GraphNode<T>[] nodes;
	private boolean isEdgeIncoming;
	private GraphNode<T> identityNode;

	public GatherFromEdgesRight(CompEnv<T> env, Machine machine, boolean isEdgeIncoming, GraphNode<T> identityNode) {
		super(env, machine);
		this.isEdgeIncoming = isEdgeIncoming;
		this.identityNode = identityNode;
	}

	public GatherFromEdgesRight<T> setInputs(GraphNode<T>[] nodes) {
		this.nodes = nodes;
		return this;
	}

	@Override
	public Object secureCompute() throws InterruptedException, IOException,
			BadLabelException, NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		if (isEdgeIncoming) {
			for (int j = 0; j < nodes.length; j++) {
				nodes[j].swapEdgeDirections();
			}
		}

		long communicate = 0;

		IntegerLib<T> lib = new IntegerLib<>(env);

		Constructor<?> constructor = nodes.getClass().getComponentType().getConstructor(new Class[]{CompEnv.class});
		GraphNode<T> graphNodeVal = identityNode.getCopy(env);
		GraphNode<T> zeroNode = (GraphNode<T>) constructor.newInstance(env);

		for (int i = nodes.length - 1; i >= 0; i--) {
			GraphNode<T> tempAgg = aggFunc(graphNodeVal, nodes[i]);
			graphNodeVal = zeroNode.mux(tempAgg, nodes[i].isVertex, env);
		}

		GraphNode<T> nodeValForLaterComp = identityNode.getCopy(env);

		int noOfIncomingConnections = machine.numberOfIncomingConnections;
		int noOfOutgoingConnections = machine.numberOfOutgoingConnections;
		for (int k = 0; k < machine.getLogMachines(); k++) {
			if (noOfOutgoingConnections > 0) {
				long one = System.nanoTime();

				machine.peersUp[k].send(nodes[0].u, env);
				graphNodeVal.send(machine.peersUp[k], env);
				machine.peersUp[k].flush();
				noOfOutgoingConnections--;

				long two = System.nanoTime();
				communicate += (two - one);
			}
			if (noOfIncomingConnections > 0) {
				long one = System.nanoTime();

				T[] prevU = machine.peersDown[k].read(nodes[0].u.length, env);
				GraphNode<T> graphNodeRead = (GraphNode<T>) constructor.newInstance(env);
				graphNodeRead.read(machine.peersDown[k], env);

				long two = System.nanoTime();
				communicate += (two - one);

				T sameU = lib.eq(prevU, nodes[nodes.length - 1].u);
				GraphNode<T> tempAgg = aggFunc(nodeValForLaterComp, graphNodeRead);
				nodeValForLaterComp = tempAgg.mux(nodeValForLaterComp, sameU, env);
				noOfIncomingConnections--;
			}
		}

		for (int i = nodes.length - 1; i >= 0; i--) {
			GraphNode<T> tempAgg = aggFunc(nodeValForLaterComp, nodes[i]);
			writeToVertex(nodeValForLaterComp, nodes[i]);
			nodeValForLaterComp = zeroNode.mux(tempAgg, nodes[i].isVertex, env);
		}


		if (isEdgeIncoming) {
			for (int j = 0; j < nodes.length; j++) {
				nodes[j].swapEdgeDirections();
			}
		}
		return communicate;
	}

	public abstract GraphNode<T> aggFunc(GraphNode<T> agg, GraphNode<T> b) throws IOException;

	public abstract void writeToVertex(GraphNode<T> agg, GraphNode<T> vertex);
}
