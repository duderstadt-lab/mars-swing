/*******************************************************************************
 * Copyright (C) 2019, Duderstadt Lab
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package de.mpg.biochem.mars.swing.plot;

import de.mpg.biochem.mars.util.LevenbergMarquardt;

public class LMplotfit extends LevenbergMarquardt {
	private static final double deltaParameter = 1e-6;
	private static final double factor = 10;

	public double chiSquared = 0.0;
	public int iterations;
	
	private LMFunction function;
	private int nParameters;
	private double precision;
	private double maxIterations;

	private double[] delta;
	private double[][] alpha;
	private double[][] beta;
	private double[][] covar;
	double[][] identityMatrix;
	
	public LMplotfit(LMFunction function, int nParameters) {
		this(function, nParameters, 0.00001, 10000);
	}
	
	public LMplotfit(LMFunction function, int nParameters, double precision, int maxIterations) {
		this.function = function;
		this.precision = precision;
		this.maxIterations = maxIterations;
		this.nParameters = nParameters;
		
		delta = new double[nParameters];
		alpha = new double[nParameters][nParameters];
		beta = new double[nParameters][1];
		covar = new double[nParameters][nParameters];
		identityMatrix = new double[nParameters][nParameters];
	}
	
	//Needs to somehow be integrated into LM better and use this method properly...
	public double getValue(double[] x, double[] p, double[] dyda) {
		return 0;	
	}
	
	private final double getChiSquared(double[] parameters, double[][] x, double[] y, double[] sigma) {
		double sumOfSquares = 0.0;
		double residual;
		
		if (sigma != null) {	// chi squared
			for (int i = 0; i < x.length; i++) {
				residual = (function.getValue(x[i], parameters) - y[i]) / sigma[i];
				sumOfSquares += residual * residual;
			}
		}
		else {	// sum of squares
			for (int i = 0; i < x.length; i++) {
				residual = function.getValue(x[i], parameters) - y[i];
				sumOfSquares += residual * residual;
			}
		}
		
		return sumOfSquares;
	}
	
	private final void calculateJacobian(double[] parameters, double[][] x, double[] y, double[][] jacobian) {
		for (int i = 0; i < nParameters; i++) {
			parameters[i] += deltaParameter;
			
			for (int j = 0; j < x.length; j++)
				jacobian[j][i] = function.getValue(x[j], parameters);
			
			parameters[i] -= 2.0 * deltaParameter;
			
			for (int j = 0; j < x.length; j++) {
				jacobian[j][i] -= function.getValue(x[j], parameters);
				jacobian[j][i] /= 2.0 * deltaParameter;
			}
			
			parameters[i] += deltaParameter;
		}
	}
	
	public double solve(double[] parameters, boolean[] vary, double[][] x, double[] y, double[] sigma, double lambda, double[] stdDev) {
		double before;
		double[][] jacobian = new double[x.length][nParameters];
		
		iterations = 0;
		chiSquared = getChiSquared(parameters, x, y, sigma);
		
		calculateJacobian(parameters, x, y, jacobian);
		
		do {
			// alpha
			for (int i = 0; i < nParameters; i++) {
				
				for (int j = 0; j < nParameters; j++) {
					alpha[j][i] = 0.0;
					
					for (int k = 0; k < x.length; k++)
						alpha[j][i] += jacobian[k][j] * jacobian[k][i];
					
					covar[j][i] = alpha[j][i];
				}
				
				alpha[i][i] += lambda * alpha[i][i];
			}
			
			// beta
			for (int i = 0; i < nParameters; i++) {
				
				beta[i][0] = 0.0;
				
				for (int j = 0; j < x.length; j++)
					beta[i][0] += jacobian[j][i] * (y[j] - function.getValue(x[j], parameters));
				
			}
			
			gaussJordan(alpha, beta);
			
			for (int i = 0; i < nParameters; i++)
				delta[i] = parameters[i] + beta[i][0];
			
			before = chiSquared;
			chiSquared = getChiSquared(delta, x, y, sigma);
			
			if (chiSquared < before) {
				lambda /= factor;
				
				// adjust parameters
				for (int i = 0; i < delta.length; i++) {
					if (vary == null || vary[i])
						parameters[i] = delta[i];
				}
				
				calculateJacobian(parameters, x, y, jacobian);
			}
			else
				lambda *= factor;
			
		} while (++iterations < maxIterations && Math.abs(before - chiSquared) > precision);
		
		if (stdDev != null) {
			// determine standard deviation of parameters
			for (int i = 0; i < identityMatrix.length; i++) {
				for (int j = 0; j < identityMatrix.length; j++)
					identityMatrix[i][j] = 0;
				identityMatrix[i][i] = 1;
			}
			
			gaussJordan(covar, identityMatrix);
			
			for (int i = 0; i < identityMatrix.length; i++)
				stdDev[i] = Math.sqrt(identityMatrix[i][i] * chiSquared / (x.length - parameters.length));
		}
		
		return lambda;
	}
	
	public final void gaussJordan(double[][] left, double[][] right) {
		int n = left.length;
		int rCols = right[0].length;
		
		for (int i = 0; i < n; i++) {
			
			// find pivot
			int max = i;
			
			for (int j = i + 1; j < n; j++) {
				if (Math.abs(left[j][i]) > Math.abs(left[max][i]))
					max = j;
			}
			
			// swap rows
			double[] t = left[i];
			left[i] = left[max];
			left[max] = t;
			
			t = right[i];
			right[i] = right[max];
			right[max] = t;
			
			// reduce
			for (int j = 0; j < n; j++) {
				
				if (j != i) {
					double d = left[j][i] / left[i][i];
					
					left[j][i] = 0;
					
					for (int k = i + 1; k < n; k++)
						left[j][k] -= d * left[i][k];
					
					for (int k = 0; k < rCols; k++)
						right[j][k] -= d * right[i][k];
				}
			}
		}
		
		for (int i = 0; i < n; i++) {
			double d = left[i][i];
			for (int k = 0; k < rCols; k++)
				right[i][k] /= d;
			left[i][i] = 1;
		}
	}
	
	private static void printMatrices(double[][] l, double[][] r) {
		
		System.out.println("left :");
		for (int i = 0; i < l.length; i++) {
			for (int j = 0; j < l[i].length; j++) 
				System.out.printf("%f, ", l[i][j]);
			System.out.println();
		}
		
		System.out.println("right :");
		for (int i = 0; i < r.length; i++) {
			for (int j = 0; j < r[i].length; j++) 
				System.out.printf("%f, ", r[i][j]);
			System.out.println();
		}
		
	}
}
