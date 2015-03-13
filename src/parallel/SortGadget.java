package parallel;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;

import network.Network;
import util.Utils;
import flexsc.CompEnv;
import gc.BadLabelException;

public class SortGadget<T> extends Gadget<T> {

	private GraphNode<T>[] nodes;
	private NodeComparator<T> comp;

	public SortGadget(CompEnv<T> env, Machine machine) {
		super(env, machine);
	}

	public SortGadget<T> setInputs(GraphNode<T>[] nodes, NodeComparator<T> comp) {
		this.nodes = nodes;
		this.comp = comp;
		return this;
	}

	@Override
	public Object secureCompute() throws InterruptedException, IOException,
			BadLabelException {
		long communicate = 0;
		long compute = 0;
		long concatenate = 0;
		long initTimer = System.nanoTime();
		GraphNodeBitonicSortLib<T> lib =  new GraphNodeBitonicSortLib<T>(env, comp);
		T dir = (machine.getGarblerId() % 2 == 0) ? lib.SIGNAL_ONE : lib.SIGNAL_ZERO;
		lib.sort(nodes, dir);

		for (int k = 0; k < machine.getLogMachines(); k++) {
			int diff = (1 << k);
			T mergeDir = ((machine.getGarblerId() / (2 * (1 << k))) % 2 == 0) ? lib.SIGNAL_ONE : lib.SIGNAL_ZERO;
			while (diff != 0) {
				boolean up = (machine.getGarblerId() / diff) % 2 == 1 ? true : false;
				Network channel;
				int commMachine = Utils.log2(diff);
				channel = up ? machine.peersUp[commMachine] : machine.peersDown[commMachine];

				long startCommunicate = System.nanoTime();
				GraphNode<T>[] receivedNodes = sendReceive(channel, nodes, nodes.length);
				long endCommunicate = System.nanoTime(), startConcatenate = System.nanoTime();

				GraphNode<T>[] concatenatedNodes = up ? concatenate(receivedNodes, nodes) : concatenate(nodes, receivedNodes); 

				long endConcatenate = System.nanoTime();
				lib.compareAndSwapFirst(concatenatedNodes, 0, concatenatedNodes.length, mergeDir);

				long startConcatenate2 = System.nanoTime();
				int srcPos = up ? concatenatedNodes.length / 2 : 0;
				System.arraycopy(concatenatedNodes, srcPos, nodes, 0, concatenatedNodes.length / 2);
				long endConcatenate2 = System.nanoTime();
				communicate += (endCommunicate - startCommunicate);
				concatenate += (endConcatenate2 - startConcatenate2) + (endConcatenate - startConcatenate);
				diff /= 2;
			}
			lib.bitonicMerge(nodes, 0, nodes.length, mergeDir);
		}
		long finalTimer = System.nanoTime();
		compute = finalTimer - initTimer - (communicate + concatenate);
		return communicate;
	}

	private GraphNode<T>[] sendReceive(Network channel, GraphNode<T>[] nodes, int arrayLength) throws IOException {
		GraphNode<T>[] a = (GraphNode<T>[]) Array.newInstance(nodes.getClass().getComponentType(), arrayLength);
		int toTransfer = nodes.length;
		int i = 0, j = 0;
		while (toTransfer > 0) {
			int curTransfer = Math.min(toTransfer, 8);
			toTransfer -= curTransfer;
			for (int k = 0; k < curTransfer; k++, i++) {
				nodes[i].send(channel, env);
			}
			channel.flush();
			for (int k = 0; k < curTransfer; k++, j++) {
				try {
					a[j] = (GraphNode<T>) nodes.getClass().getComponentType().getConstructor(new Class[]{CompEnv.class}).newInstance(env);
				} catch (IllegalArgumentException | InvocationTargetException
						| NoSuchMethodException | SecurityException
						| InstantiationException | IllegalAccessException e) {
					e.printStackTrace();
					System.exit(1);
				}
				a[j].read(channel, env);
			}
		}
		return a;
	}

	public <T> T[] concatenate(T[] A, T[] B) {
	    int aLen = A.length;
	    int bLen = B.length;

	    @SuppressWarnings("unchecked")
	    T[] C = (T[]) Array.newInstance(A.getClass().getComponentType(), aLen+bLen);
	    System.arraycopy(A, 0, C, 0, aLen);
	    System.arraycopy(B, 0, C, aLen, bLen);

	    return C;
	}
}
