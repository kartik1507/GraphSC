package parallel;


public interface NodeComparator<T> {

	public T leq(GraphNode<T> node1, GraphNode<T> node2);
}
