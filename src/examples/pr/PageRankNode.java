package examples.pr;

import parallel.GraphNode;
import util.Utils;
import flexsc.CompEnv;

public class PageRankNode<T> extends GraphNode<T> {
	T[] pr;
	T[] l;
	static int OFFSET = 20;
	static int WIDTH = 40;

	public PageRankNode(T[] u, T[] v, T isVertex, CompEnv<T> env) {
		super(u, v, isVertex);
		this.pr = env.newTArray(PageRankNode.WIDTH);
		this.l = env.newTArray(PageRankNode.WIDTH);
	}

	public PageRankNode(CompEnv<T> env) {
		super(env);
		this.pr = env.inputOfAlice(Utils.fromFixPoint(0, PageRankNode.WIDTH, PageRankNode.OFFSET));
		this.l = env.inputOfAlice(Utils.fromFixPoint(0, PageRankNode.WIDTH, PageRankNode.OFFSET));
	}

	@Override
	public T[] flatten(CompEnv<T> env) {
		T[] vert = env.newTArray(1);
		vert[0] = (T) isVertex;
		return Utils.flatten(env, u, v, pr, l, vert);
	}

	@Override
	public void unflatten(T[] flat, CompEnv<T> env) {
		T[] vert = env.newTArray(1);
		Utils.unflatten(flat, u, v, pr, l, vert);
		isVertex = vert[0];
	}
}
