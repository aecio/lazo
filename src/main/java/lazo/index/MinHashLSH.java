package lazo.index;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lazo.sketch.Sketch;

/**
 * This MinHash LSH implementation is based on the datasketch one
 * (https://ekzhu.github.io/datasketch/lsh.html)
 * 
 * @author Raul - raulcf@csail.mit.edu
 */
public class MinHashLSH {

    private float threshold;
    private int k;
    private int bands;
    private int rows;

    private List<Map<Long, List<Object>>> hashTables;
    private int[] hashRanges;

    // integration precision
    private float IP = 0.001f;

    public int __getNumberHashTables() {
	return this.hashTables.size();
    }

    public MinHashLSH(float threshold, int k) {
	if (threshold < 0 || threshold > 1) {
	    throw new IllegalArgumentException("Threshold must be in the range [0,1]");
	}
	if (k <= 0) {
	    throw new IllegalArgumentException("The number of permutations must be positive (> 0)");
	}
	this.threshold = threshold;
	this.k = k;
	// 0.5 are good default values for false positive and negative
	this.computeOptimalParameters(this.threshold, this.k, 0.5f, 0.5f);

	this.initializeHashTables(bands);
    }

    public MinHashLSH(float threshold, int k, float false_positive_rate, float false_negative_rate) {
	if (threshold < 0 || threshold > 1) {
	    throw new IllegalArgumentException("Threshold must be in the range [0,1]");
	}
	if (k <= 0) {
	    throw new IllegalArgumentException("The number of permutations must be positive (> 0)");
	}
	this.threshold = threshold;
	this.k = k;
	this.computeOptimalParameters(this.threshold, this.k, false_positive_rate, false_negative_rate);

	this.initializeHashTables(bands);
    }

    public MinHashLSH(float threshold, int k, int bands, int rows) {
	if (threshold < 0 || threshold > 1) {
	    throw new IllegalArgumentException("Threshold must be in the range [0,1]");
	}
	if (k <= 0) {
	    throw new IllegalArgumentException("The number of permutations must be positive (> 0)");
	}
	// FIXME: cannot be much smaller either, although for numerical reasons,
	// we may lose a couple of
	// permutations depending on the specific k value given by the user. One
	// option is to disallow that
	// for soundness. Other option is to just check that the value is not
	// 'too far' from k.
	if (rows * bands > k) {
	    throw new IllegalArgumentException("Bands * Rows cannot be larger than k");
	}
	this.threshold = threshold;
	this.k = k;
	this.bands = bands;
	this.rows = rows;

	this.initializeHashTables(bands);
    }

    private void initializeHashTables(int bands) {
	this.hashTables = new ArrayList<>();
	this.hashRanges = new int[this.bands];
	// hash tables
	for (int i = 0; i < bands; i++) {
	    Map<Long, List<Object>> mp = new HashMap<>();
	    hashTables.add(mp);
	}
	// hash ranges
	for (int i = 0; i < hashRanges.length; i++) {
	    hashRanges[i] = i * this.rows;
	}
    }

    private float computeFalsePositiveProbability(float threshold, int bands, int rows) {
	float start = 0.0f;
	float end = threshold;
	float area = 0.0f;
	float x = start;
	while (x < end) {
	    area += 1 - Math.pow((1 - Math.pow(x + 0.5 * IP, rows)), bands) * IP;
	    x = x + IP;
	}
	return (float) area;
    }

    private float computeFalseNegativeProbability(float threshold, int bands, int rows) {
	float start = threshold;
	float end = 1.0f;
	float area = 0.0f;
	float x = start;
	while (x < end) {
	    area += 1 - (1 - Math.pow((1 - Math.pow(x + 0.5 * IP, rows)), bands) * IP);
	    x = x + IP;
	}
	return (float) area;
    }

    private void computeOptimalParameters(float threshold, int k, float fp_rate, float fn_rate) {
	float minError = Float.MAX_VALUE;
	int optimalBands = 0;
	int optimalRows = 0;

	int maximumRows = 0;
	for (int band = 1; band < k + 1; band++) {
	    maximumRows = (int) (k / band);
	    for (int rows = 1; rows < maximumRows + 1; rows++) {
		float falsePositives = this.computeFalsePositiveProbability(threshold, band, rows);
		float falseNegatives = this.computeFalseNegativeProbability(threshold, band, rows);
		float error = fp_rate * falsePositives + fn_rate * falseNegatives;
		if (error < minError) {
		    minError = error;
		    optimalBands = band;
		    optimalRows = rows;
		}
	    }
	}
	// FIXME; return this in a tuple or similar; set in constructor instead
	this.rows = optimalRows;
	this.bands = optimalBands;
    }

    private long segmentHash(long[] segment) {
	return Arrays.hashCode(segment);
    }

    public boolean insert(Object key, Sketch mh) {

	List<long[]> segments = new ArrayList<>();
	for (int start : this.hashRanges) {
	    int end = start + this.rows;
	    long[] segment = Arrays.copyOfRange(mh.getHashValues(), start, end);
	    segments.add(segment);
	}

	for (int i = 0; i < this.bands; i++) {
	    long[] sg = segments.get(i);
	    Map<Long, List<Object>> hashTable = hashTables.get(i);
	    long segId = segmentHash(sg);
	    if (hashTable.get(segId) == null) {
		List<Object> l = new ArrayList<>();
		hashTable.put(segId, l);
	    }
	    hashTable.get(segId).add(key);
	}

	return true;
    }

    public Set<Object> query(Sketch mh) {
	Set<Object> candidates = new HashSet<>();
	for (int i = 0; i < this.bands; i++) {
	    int start = this.hashRanges[i];
	    int end = (this.hashRanges[i] + 1) * this.rows;
	    Map<Long, List<Object>> hashTable = hashTables.get(i);
	    long[] segment = Arrays.copyOfRange(mh.getHashValues(), start, end);
	    long segId = segmentHash(segment);
	    if (hashTable.containsKey(segId)) {
		candidates.addAll(hashTable.get(segId));
	    }
	}
	return candidates;
    }

}
