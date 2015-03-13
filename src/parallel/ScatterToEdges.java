package parallel;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import circuits.arithmetic.IntegerLib;
import flexsc.CompEnv;
import gc.BadLabelException;

public abstract class ScatterToEdges<T> extends Gadget<T> {

	private GraphNode<T>[] nodes;
	private boolean isEdgeIncoming;

	public ScatterToEdges(CompEnv<T> env, Machine machine, boolean isEdgeIncoming) {
		super(env, machine);
		this.isEdgeIncoming = isEdgeIncoming;
	}

	public ScatterToEdges<T> setInputs(GraphNode<T>[] prNodes) {
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

		long communicate = (long) new SortGadget<T>(env, machine)
			.setInputs(nodes, nodes[0].getComparator(env, false /* isVertexLast */))
			.secureCompute();

		IntegerLib<T> lib = new IntegerLib<>(env);
		T _true = env.newT(true);

		T foundToSend = env.newT(false);
		Class<?> componentType = nodes.getClass().getComponentType();
		Constructor<?> constructor = componentType.getConstructor(new Class[]{CompEnv.class});
		GraphNode<T> graphNode = (GraphNode<T>) constructor.newInstance(env);

		for (int i = 0; i < nodes.length; i++) {
			foundToSend = lib.mux(foundToSend, _true, nodes[i].isVertex); // always sent
			graphNode = nodes[i].mux(graphNode, nodes[i].isVertex, env);
		}

		T found = env.newT(false);
		GraphNode<T> graphNodeLast = (GraphNode<T>) constructor.newInstance(env);

		int noOfIncomingConnections = machine.numberOfIncomingConnections;
		int noOfOutgoingConnections = machine.numberOfOutgoingConnections;
		for (int k = 0; k < machine.getLogMachines(); k++) {
			if (noOfIncomingConnections > 0) {
				long one = System.nanoTime();

				machine.peersDown[k].send(foundToSend, env);
				graphNode.send(machine.peersDown[k], env);
				machine.peersDown[k].flush();
				noOfIncomingConnections--;

				long two = System.nanoTime();
				communicate += (two - one);
			}
			if (noOfOutgoingConnections > 0) {
				long one = System.nanoTime();

				T foundRead = machine.peersUp[k].read(env);
				GraphNode<T> graphNodeRead = (GraphNode<T>) constructor.newInstance(env);
				graphNodeRead.read(machine.peersUp[k], env);

				long two = System.nanoTime();
				communicate += (two - one);

				// compute the value for the last vertex
				graphNodeLast = graphNodeLast.mux(graphNodeRead, found, env);
				found = lib.mux(found, _true, foundRead);
				noOfOutgoingConnections--;
			}
		}

		// found will always be true in the end!
		for (int i = 0; i < nodes.length; i++) {
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
