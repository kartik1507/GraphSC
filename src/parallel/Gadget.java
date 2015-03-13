package parallel;

import flexsc.CompEnv;

public abstract class Gadget<T> {
	protected CompEnv<T> env;
	protected Machine machine;

	abstract public Object secureCompute() throws Exception;

	public Gadget(CompEnv<T> env, Machine machine) {
		this.env = env;
		this.machine = machine;
	}
}