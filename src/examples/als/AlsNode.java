package examples.als;

import parallel.GraphNode;
import util.Utils;
import circuits.arithmetic.ArithmeticLib;
import circuits.arithmetic.FixedPointLib;
import circuits.arithmetic.IntegerLib;
import flexsc.CompEnv;

public class AlsNode<T> extends GraphNode<T> {

	public static final int FIX_POINT_WIDTH = 40;
	public static final int OFFSET = 20;

	public T[] rating;
	public T[][] up;
	public T[][] vp;
	public T isU;
	public T isV;
	public T[][][] M;

	ArithmeticLib<T> flib;
	IntegerLib<T> lib;

	public AlsNode(CompEnv<T> env) {
		super(env);
		flib = new FixedPointLib<>(env, FIX_POINT_WIDTH, OFFSET);
		lib = new IntegerLib<T>(env);
		// initialize all to 0
		rating = env.inputOfAlice(Utils.fromFixPoint(0, FIX_POINT_WIDTH, OFFSET));
		up = env.newTArray(Als.D, FIX_POINT_WIDTH);
		vp = env.newTArray(Als.D, FIX_POINT_WIDTH);
		M = env.newTArray(Als.D, Als.D, FIX_POINT_WIDTH);
		for (int i = 0; i < Als.D; ++i) {
			up[i] = env.inputOfAlice(Utils.fromFixPoint(0, FIX_POINT_WIDTH, OFFSET));
			vp[i] = env.inputOfAlice(Utils.fromFixPoint(0, FIX_POINT_WIDTH, OFFSET));
			for (int j = 0; j < Als.D; j++) {
				M[i][j] = env.inputOfAlice(Utils.fromFixPoint(0, FIX_POINT_WIDTH, OFFSET));
			}
		}
		this.isU = env.inputOfAlice(false);
		this.isV = env.inputOfAlice(false);
	}

	public AlsNode(T[] u,
			T[] v,
			T isVertex,
			T[] rating,
			T[][] up,
			T[][] vp,
			T isU,
			T isV,
			CompEnv<T> env) {
		super(u, v, isVertex);
		flib = new FixedPointLib<>(env, FIX_POINT_WIDTH, OFFSET);
		lib = new IntegerLib<T>(env);
		this.rating = rating;
		this.up = up;
		this.vp = vp;
		M = env.newTArray(Als.D, Als.D, FIX_POINT_WIDTH);
		for (int i = 0; i < Als.D; ++i) {
			for (int j = 0; j < Als.D; j++) {
				M[i][j] = env.inputOfAlice(Utils.fromFixPoint(0, FIX_POINT_WIDTH, OFFSET));
			}
		}
		this.isU = isU;
		this.isV = isV;
	}

	public void solveU(CompEnv<T> env) {
		T[] tempM = env.newTArray(Als.D);
		T[] tempVp = env.newTArray(Als.D);
		for (int i = 0; i < Als.D; i++) {
			for (int j = 0; j < Als.D; j++) {
				tempM = flib.multiply(vp[i], vp[j]);
				M[i][j] = lib.mux(tempM, M[i][j], isVertex);
			}
		}
		for (int i = 0; i < Als.D; i++) {
			tempVp = flib.multiply(rating, vp[i]);
			vp[i] = lib.mux(tempVp, vp[i], isVertex);
		}
	}

	public void solveV(CompEnv<T> env) {
		T[] tempM = env.newTArray(Als.D);
		T[] tempUp = env.newTArray(Als.D);
		for (int i = 0; i < Als.D; i++) {
			for (int j = 0; j < Als.D; j++) {
				tempM = flib.multiply(up[i], up[j]);
				M[i][j] = lib.mux(tempM, M[i][j], isVertex);
			}
		}
		for (int i = 0; i < Als.D; i++) {
			tempUp = flib.multiply(rating, up[i]);
			up[i] = lib.mux(tempUp, up[i], isVertex);
		}
	}

	@Override
	public T[] flatten(CompEnv<T> env) {
		T[] vert = env.newTArray(3);
		vert[0] = (T) isVertex;
		vert[1] = (T) isU;
		vert[2] = (T) isV;
		T[] flattenedUserProfile = Utils.flatten(env, up);
		T[] flattenedItemProfile = Utils.flatten(env, vp);
		T[] flattenedM = Utils.flatten(env, Utils.flatten(env, M));
		return Utils.flatten(env, u, v, vert, rating, flattenedUserProfile, flattenedItemProfile, flattenedM);
	}

	@Override
	public void unflatten(T[] flat, CompEnv<T> env) {
		T[] vert = env.newTArray(3);
		T[] flattenedUserProfile = env.newTArray(Als.D * FIX_POINT_WIDTH);
		T[] flattenedItemProfile = env.newTArray(Als.D * FIX_POINT_WIDTH);
		T[] flattenedM = env.newTArray(Als.D * Als.D * FIX_POINT_WIDTH);
		Utils.unflatten(flat, u, v, vert, rating, flattenedUserProfile, flattenedItemProfile, flattenedM);
		Utils.unflatten(flattenedUserProfile, up);
		Utils.unflatten(flattenedItemProfile, vp);
		T[][] m = env.newTArray(Als.D, Als.D * FIX_POINT_WIDTH);
		Utils.unflatten(flattenedM, m);
		for (int i = 0; i < Als.D; i++) {
			Utils.unflatten(m[i], M[i]);
		}
		isVertex = vert[0];
		isU = vert[1];
		isV = vert[2];
	}

}
