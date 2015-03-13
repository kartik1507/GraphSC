package parallel;

import circuits.arithmetic.IntegerLib;
import flexsc.CompEnv;

public class GraphNodeBitonicSortLib<T> extends IntegerLib<T> {
	T isAscending;
	NodeComparator<T> comparator;

	public GraphNodeBitonicSortLib(CompEnv<T> e, NodeComparator<T> comparator) {
		super(e);
		this.comparator = comparator;
	}

	public void sort(GraphNode<T>[] nodes, T isAscending) {
		this.isAscending = isAscending;
		bitonicSort(nodes, 0, nodes.length, isAscending);
	}

	private void bitonicSort(GraphNode<T>[] nodes, int start, int n, T dir) {
		if (n > 1) {
			int m = n / 2;
			bitonicSort(nodes, start, m, isAscending);
			bitonicSort(nodes, start + m, n - m, not(isAscending));
			bitonicMerge(nodes, start, n, dir);
//			bitonicSort(a, data, start, m, not(dir));
//			bitonicSort(a, data, start + m, n - m, dir);
//			bitonicMerge(a, data, start, n, dir);
		}
	}

	public void bitonicMerge(GraphNode<T>[] nodes, int start, int n, T dir) {
		if (n > 1) {
			int m = compareAndSwapFirst(nodes, start, n, dir);
			bitonicMerge(nodes, start, m, dir);
			bitonicMerge(nodes, start + m, n - m, dir);
		}
	}

	public int compareAndSwapFirst(GraphNode<T>[] nodes, int start, int n, T dir) {
		int m = greatestPowerOfTwoLessThan(n);
		for (int i = start; i < start + n - m; i++) {
			compareAndSwap(nodes, i, i + m, dir);
		}
		return m;
	}

	private void compareAndSwap(GraphNode<T>[] nodes, int i, int j, T dir) {
    	T greater = not(comparator.leq(nodes[i], nodes[j]));
    	T swap = eq(greater, dir);

    	T[] ni = nodes[i].flatten(env);
    	T[] nj = nodes[j].flatten(env);

    	IntegerLib<T> lib = new IntegerLib<>(env, ni.length);
    	T[] s = lib.mux(nj, ni, swap);
    	s = lib.xor(s, ni);
    	T[] ki = lib.xor(nj, s);
    	T[] kj = lib.xor(ni, s);
    	ni = ki;
    	nj = kj;

    	nodes[i].unflatten(ni, env);
    	nodes[j].unflatten(nj, env);
    }

	private int greatestPowerOfTwoLessThan(int n) {
        int k = 1;
        while(k < n)
            k = k << 1;
        return k >> 1;
    }
}
