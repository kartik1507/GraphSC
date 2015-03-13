package parallel;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import circuits.arithmetic.IntegerLib;
import flexsc.CompEnv;
import gc.BadLabelException;

public abstract class ScatterToEdgesRight<T> extends Gadget<T> {

	private GraphNode<T>[] nodes;
	private boolean isEdgeIncoming;

	public ScatterToEdgesRight(CompEnv<T> env, Machine machine, boolean isEdgeIncoming) {
		super(env, machine);
		this.isEdgeIncoming = isEdgeIncoming;
	}

	public ScatterToEdgesRight<T> setInputs(GraphNode<T>[] prNodes) {
		this.nodes = prNodes;
		return this;
	}

	@Override
	public Object secureCompute() throws InterruptedException, IOException,
			BadLabelException, InstantiationException, IllegalAccessException, NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
		if (isEdgeIncoming) {
			for (int j = 0; j < nodes.length; j++) {
				nodes[j].swapEdgeDirections();
			}
		}

		long communicate = 0;

		IntegerLib<T> lib = new IntegerLib<>(env);
		T _true = env.newT(true);

		T foundToSend = env.newT(false);
		Class<?> componentType = nodes.getClass().getComponentType();
		Constructor<?> constructor = componentType.getConstructor(new Class[]{CompEnv.class});
		GraphNode<T> graphNode = (GraphNode<T>) constructor.newInstance(env);

		for (int i = nodes.length - 1; i >= 0; i--) {
			foundToSend = lib.mux(foundToSend, _true, nodes[i].isVertex); // always sent
			graphNode = nodes[i].mux(graphNode, nodes[i].isVertex, env);
		}

		T found = env.newT(false);
		GraphNode<T> graphNodeLast = (GraphNode<T>) constructor.newInstance(env);

		int noOfIncomingConnections = machine.numberOfIncomingConnections;
		int noOfOutgoingConnections = machine.numberOfOutgoingConnections;
		for (int k = 0; k < machine.getLogMachines(); k++) {
			if (noOfOutgoingConnections > 0) {
				long one = System.nanoTime();

				machine.peersUp[k].send(foundToSend, env);
				graphNode.send(machine.peersUp[k], env);
				machine.peersUp[k].flush();
				noOfOutgoingConnections--;

				long two = System.nanoTime();
				communicate += (two - one);
			}
			if (noOfIncomingConnections > 0) {
				long one = System.nanoTime();

				T foundRead = machine.peersDown[k].read(env);
				GraphNode<T> graphNodeRead = (GraphNode<T>) constructor.newInstance(env);
				graphNodeRead.read(machine.peersDown[k], env);

				long two = System.nanoTime();
				communicate += (two - one);

				// compute the value for the last vertex
				graphNodeLast = graphNodeLast.mux(graphNodeRead, found, env);
				found = lib.mux(found, _true, foundRead);
				noOfIncomingConnections--;
			}
		}

		// found will always be true in the end!
		for (int i = nodes.length - 1; i >= 0; i--) {
			writeToEdge(graphNodeLast, nodes[i], nodes[i].isVertex);
			graphNodeLast = nodes[i].mux(graphNodeLast, nodes[i].isVertex, env);
		}

		if (isEdgeIncoming) {
			for (int j = 0; j < nodes.length; j++) {
				nodes[j].swapEdgeDirections();
			}
		}
		return communicate;
	}

	public abstract void writeToEdge(GraphNode<T> vertexNode, GraphNode<T> edgeNode, T isVertex);
}
