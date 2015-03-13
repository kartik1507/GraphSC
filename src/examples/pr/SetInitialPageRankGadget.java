package examples.pr;

import java.io.IOException;

import parallel.Gadget;
import parallel.Machine;
import util.Utils;
import circuits.arithmetic.IntegerLib;
import flexsc.CompEnv;
import gc.BadLabelException;

public class SetInitialPageRankGadget<T> extends Gadget<T> {

	private PageRankNode<T>[] prNodes;

	public SetInitialPageRankGadget(CompEnv<T> env,
			Machine machine) {
		super(env, machine);
	}

	public SetInitialPageRankGadget<T> setInputs(PageRankNode<T>[] prNodes) {
		this.prNodes = prNodes;
		return this;
	}

	@Override
	public Object secureCompute() throws InterruptedException, IOException,
			BadLabelException {
		IntegerLib<T> intLib = new IntegerLib<T>(env);
		T[] one = env.inputOfAlice(Utils.fromFixPoint(1, PageRankNode.WIDTH, PageRankNode.OFFSET));
		T[] zero = env.inputOfAlice(Utils.fromFixPoint(0, PageRankNode.WIDTH, PageRankNode.OFFSET));
		for (int i = 0; i < prNodes.length; i++) {
			prNodes[i].pr = intLib.mux(zero, one, prNodes[i].isVertex);
			prNodes[i].l = intLib.mux(one, zero, prNodes[i].isVertex);
		}
		return null;
	}

}
