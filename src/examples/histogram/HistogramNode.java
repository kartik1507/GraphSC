package examples.histogram;

import parallel.GraphNode;
import util.Utils;
import circuits.arithmetic.IntegerLib;
import flexsc.CompEnv;

public class HistogramNode<T> extends GraphNode<T> {

	static int LEN = 20;

	T[] count;
	IntegerLib<T> lib;

	public HistogramNode(T[] u, T[] v, T isVertex, CompEnv<T> env) {
		super(u, v, isVertex);
		lib = new IntegerLib<>(env);
		T[] one = env.inputOfAlice(Utils.fromInt(1, HistogramNode.LEN));
		T[] zero = env.inputOfAlice(Utils.fromInt(0, HistogramNode.LEN));
		this.count = lib.mux(one, zero, isVertex);
	}

	public HistogramNode(CompEnv<T> env) {
		super(env);
		lib = new IntegerLib<>(env);
		this.count = env.inputOfAlice(Utils.fromInt(0, LEN));
	}

	@Override
	public T[] flatten(CompEnv<T> env) {
		T[] vert = env.newTArray(1);
		vert[0] = (T) isVertex;
		return Utils.flatten(env, u, v, vert, count);
	}

	@Override
	public void unflatten(T[] flat, CompEnv<T> env) {
		T[] vert = env.newTArray(1);
		Utils.unflatten(flat, u, v, vert, count);
		isVertex = vert[0];
	}

}
