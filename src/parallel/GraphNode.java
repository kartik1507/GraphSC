package parallel;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import network.Network;
import util.Utils;
import circuits.arithmetic.IntegerLib;
import flexsc.CompEnv;

public abstract class GraphNode<T> {

	public static int VERTEX_LEN = 16;

	public T[] u;
	public T[] v;
	public T isVertex;

	public GraphNode(T[] u, T[] v, T isVertex) {
		this.u = u;
		this.v = v;
		this.isVertex = isVertex;
	}

	public GraphNode(CompEnv<T> env) {
		this.u = env.inputOfAlice(Utils.fromInt(0, VERTEX_LEN));
		this.v = env.inputOfAlice(Utils.fromInt(0, VERTEX_LEN));
		this.isVertex = env.inputOfAlice(false);
	}

	public GraphNode() {

	}

	public void send(Network channel, CompEnv<T> env) throws IOException {
		T[] flattened = this.flatten(env);
		channel.send(flattened, env);
	}

	public void read(Network channel, CompEnv<T> env) throws IOException {
		T[] flattened = channel.read(this.flatten(env).length, env);
		this.unflatten(flattened, env);
	}

	public GraphNode<T> mux(GraphNode<T> b, T condition, CompEnv<T> env) {
		T[] b1 = b.flatten(env);
		T[] this1 = this.flatten(env);
		IntegerLib<T> lib = new IntegerLib<T>(env);
		T[] flattened = lib.mux(b1, this1, condition);
		GraphNode<T> ret = null;
		ret = getGraphNodeObject(env);
		ret.unflatten(flattened, env);
		return ret;
	}

	private GraphNode<T> getGraphNodeObject(CompEnv<T> env) {
		GraphNode<T> ret = null;
		try {
			ret = (GraphNode<T>) this.getClass().getConstructor(new Class[]{CompEnv.class}).newInstance(env);
		} catch (IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException
				| InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
			System.exit(1);
		}
		return ret;
	}

	public GraphNode<T> getCopy(CompEnv<T> env) {
		T[] flattened = this.flatten(env);
		T[] copy = env.newTArray(flattened.length);
		System.arraycopy(flattened, 0, copy, 0, flattened.length);
		GraphNode<T> ret = getGraphNodeObject(env);
		((GraphNode<T>) ret).unflatten(copy, env);
		return ret;
	}

	public void swapEdgeDirections() {
		T[] temp = this.u;
		this.u = this.v;
		this.v = temp;
	}

	public abstract T[] flatten(CompEnv<T> env);

	public abstract void unflatten(T[] flat, CompEnv<T> env);

	// sort on u and have the vertex last/first
	public NodeComparator<T> getComparator(final CompEnv<T> env, final boolean isVertexLast) {
		NodeComparator<T> firstSortComparator = new NodeComparator<T>() {

			@Override
			public T leq(GraphNode<T> n1, GraphNode<T> n2) {
				IntegerLib<T> lib = new IntegerLib<>(env, GraphNode.VERTEX_LEN + 1);
				T[] v1 = env.newTArray(1), v2 = env.newTArray(1);
				if (isVertexLast) {
					v1[0] = n1.isVertex;
					v2[0] = n2.isVertex;
				} else {
					v1[0] = lib.not(n1.isVertex);
					v2[0] = lib.not(n2.isVertex);
				}
				T[] ai = (T[]) Utils.flatten(env, v1, n1.u);
				T[] aj = (T[]) Utils.flatten(env, v2, n2.u);
				return lib.leq(ai, aj);
			}
		};
		return firstSortComparator;
	}

	public static <T> NodeComparator<T> allVerticesFirst(final CompEnv<T> env) {
		NodeComparator<T> firstSortComparator = new NodeComparator<T>() {

			@Override
			public T leq(GraphNode<T> n1, GraphNode<T> n2) {
				IntegerLib<T> lib = new IntegerLib<>(env, GraphNode.VERTEX_LEN + 1);

				T[] v1 = env.newTArray(2), v2 = env.newTArray(2);
				v1[1] = env.ZERO();
				v2[1] = env.ZERO();
				v1[0] = lib.not(n1.isVertex);
				v2[0] = lib.not(n2.isVertex);
				T[] ai = (T[]) Utils.flatten(env, n1.u, v1);
				T[] aj = (T[]) Utils.flatten(env, n2.u, v2);
				return lib.leq(ai, aj);
			}
		};
		return firstSortComparator;
	}
}