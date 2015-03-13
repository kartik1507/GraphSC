package examples.histogram;

import java.util.Random;

public class HistogramGenerator {

	public static void main(String args[]) {
		int inputLength = Integer.parseInt(args[0]);
		int[] u = new int[inputLength];
		int[] v = new int[inputLength];
		boolean[] isVertex = new boolean[inputLength];
		int limit = 4;
		for (int i = 0; i < limit; i++) {
			u[i] = i + 1;
			v[i] = i + 1;
			isVertex[i] = true;
		}
		Random rn = new Random();
		int[] freq = new int[limit + 1];
		for (int i = 0; i < limit + 1; i++)
			freq[i] = 0;
		for (int i = limit; i < u.length; ++i) {
			u[i] = rn.nextInt(limit) + 1;
			v[i] = u[i];
			isVertex[i] = false;
			freq[v[i]]++;
		}
		for (int i = 0; i <= 4; i++)
			System.err.println(i + ": " + freq[i]);
		for (int i = 0; i < inputLength; i++) {
			int write = 0;
			if (isVertex[i]) {
				write = 1;
			}
			System.out.println(u[i] + " " + v[i] + " " + write);
		}
	}
}
