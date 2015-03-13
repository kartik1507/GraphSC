package examples.gd;

import parallel.GraphNode;
import util.Utils;
import circuits.arithmetic.FixedPointLib;
import circuits.arithmetic.IntegerLib;
import flexsc.CompEnv;

public class MFNode<T> extends GraphNode<T> {
	public static final int FIX_POINT_WIDTH = 40;
	public static final int OFFSET = 20;

	public static final int D = 10;

	public T[] rating;
	public T[][] userProfile;
	public T[][] itemProfile;

	FixedPointLib<T> lib;
	IntegerLib<T> ilib;

	public MFNode(CompEnv<T> env) {
		super(env);
		lib = new FixedPointLib<>(env, FIX_POINT_WIDTH, OFFSET);
		ilib = new IntegerLib<T>(env);
		// initialize all to 0
		rating = env.inputOfAlice(Utils.fromFixPoint(0, FIX_POINT_WIDTH, OFFSET));
		userProfile = env.newTArray(D, FIX_POINT_WIDTH);
		itemProfile = env.newTArray(D, FIX_POINT_WIDTH);
		for (int i = 0; i < D; ++i) {
			userProfile[i] = env.inputOfAlice(Utils.fromFixPoint(0, FIX_POINT_WIDTH, OFFSET));
			itemProfile[i] = env.inputOfAlice(Utils.fromFixPoint(0, FIX_POINT_WIDTH, OFFSET));
		}
	}

	public MFNode(T[] u,
			T[] v,
			T isVertex,
			T[] rating,
			T[][] userProfile,
			T[][] itemProfile,
			CompEnv<T> env) {
		super(u, v, isVertex);
		lib = new FixedPointLib<>(env, FIX_POINT_WIDTH, OFFSET);
		ilib = new IntegerLib<T>(env);
		this.rating = rating;
		this.userProfile = userProfile;
		this.itemProfile = itemProfile;
	}

	public int numberOfBits() {
		return VERTEX_LEN * 2 + 1 + FIX_POINT_WIDTH * (2 * D + 1);
	}

	public void computeGradient(double gamma, CompEnv<T> env) {
		T[] twoGamma = lib.publicValue(gamma * 2);

		T[] innerProductResult = innerProduct(userProfile, itemProfile);
		T[] scalar = lib.sub(rating, innerProductResult);
		scalar = lib.multiply(twoGamma, scalar);
		T[][] newUserProfile = multiplyToVector(scalar, itemProfile, env);

		T[][] newItemProfile = multiplyToVector(scalar, userProfile, env);

		for (int i = 0; i < userProfile.length; ++i)
			userProfile[i] = ilib.mux(userProfile[i], newUserProfile[i],
					ilib.not(isVertex));

		for (int i = 0; i < itemProfile.length; ++i)
			itemProfile[i] = ilib.mux(itemProfile[i], newItemProfile[i],
					ilib.not(isVertex));
	}

	public T[] innerProduct(T[][] userProfile, T[][] itemProfile) {
		T[] res = lib.publicValue(0);
		for (int i = 0; i < userProfile.length; ++i)
			res = lib.add(lib.multiply(userProfile[i], itemProfile[i]), res);
		return res;
	}

	public T[][] multiplyToVector(T[] scalar, T[][] vector, CompEnv<T> env) {
		T[][] res = env.newTArray(vector.length, 1);
		for (int i = 0; i < vector.length; ++i)
			res[i] = lib.multiply(vector[i], scalar);
		return res;
	}

	@Override
	public T[] flatten(CompEnv<T> env) {
		T[] vert = env.newTArray(1);
		vert[0] = (T) isVertex;
		T[] flattenedUserProfile = Utils.flatten(env, userProfile);
		T[] flattenedItemProfile = Utils.flatten(env, itemProfile);
		return Utils.flatten(env, u, v, vert, rating, flattenedUserProfile, flattenedItemProfile);
	}

	@Override
	public void unflatten(T[] flat, CompEnv<T> env) {
		T[] vert = env.newTArray(1);
		T[] flattenedUserProfile = env.newTArray(D * FIX_POINT_WIDTH);
		T[] flattenedItemProfile = env.newTArray(D * FIX_POINT_WIDTH);
		Utils.unflatten(flat, u, v, vert, rating, flattenedUserProfile, flattenedItemProfile);
		Utils.unflatten(flattenedUserProfile, userProfile);
		Utils.unflatten(flattenedItemProfile, itemProfile);
		isVertex = vert[0];
	}
}