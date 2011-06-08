/*
 * BirthDeathGernhard08Model.java
 *
 * Copyright (C) 2002-2009 Alexei Drummond and Andrew Rambaut
 *
 * This file is part of BEAST.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * BEAST is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * BEAST is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package dr.evomodel.speciation;

import dr.evolution.tree.NodeRef;
import dr.evolution.tree.Tree;
import dr.evomodelxml.speciation.BirthDeathModelParser;
import dr.inference.model.Parameter;
import static org.apache.commons.math.special.Gamma.logGamma;

/**
 * Birth Death model based on Gernhard 2008  "The conditioned reconstructed process"
 * Journal of Theoretical Biology Volume 253, Issue 4, 21 August 2008, Pages 769-778
 * doi:10.1016/j.jtbi.2008.04.005 (http://dx.doi.org/10.1016/j.jtbi.2008.04.005)
 * <p/>
 * This derivation conditions directly on fixed N taxa.
 * <p/>
 * The inference is directly on b-d (strictly positive) and d/b (constrained in [0,1))
 * <p/>
 * Vefified using simulated trees generated by Klass tree sample. (http://www.klaashartmann.com/treesample/)
 * <p/>
 * Sampling proportion not verified via simulation. Proportion set by default to 1, an assignment which makes the expressions
 * identical to the expressions before the change.
 *
 * @author Joseph Heled
 *         Date: 24/02/2008
 */
public class BirthDeathGernhard08Model extends UltrametricSpeciationModel {

    public enum TreeType {
        UNSCALED,     // no coefficient 
        TIMESONLY,    // n!
        ORIENTED,     // n
        LABELED,      // 2^(n-1)/(n-1)!  (conditional on root: 2^(n-1)/n!(n-1) )
    }

    public static final String BIRTH_DEATH_MODEL = BirthDeathModelParser.BIRTH_DEATH_MODEL;

    /*
     * mu/lambda
     *
     * null means default (0), or pure birth (Yule)
     */
    private Parameter relativeDeathRateParameter;

    /**
     *    lambda - mu
     */
    private Parameter birthDiffRateParameter;
   
    private Parameter sampleProbability;

    private TreeType type;

    private boolean conditionalOnRoot;

    /**
     * rho *
     */

    public BirthDeathGernhard08Model(Parameter birthDiffRateParameter,
                                     Parameter relativeDeathRateParameter,
                                     Parameter sampleProbability,
                                     TreeType type,
                                     Type units) {

        this(BIRTH_DEATH_MODEL, birthDiffRateParameter, relativeDeathRateParameter, sampleProbability, type, units, false);
    }

    public BirthDeathGernhard08Model(String modelName,
                                     Parameter birthDiffRateParameter,
                                     Parameter relativeDeathRateParameter,
                                     Parameter sampleProbability,
                                     TreeType type,
                                     Type units, boolean conditionalOnRoot) {

        super(modelName, units);

        this.birthDiffRateParameter = birthDiffRateParameter;
        addVariable(birthDiffRateParameter);
        birthDiffRateParameter.addBounds(new Parameter.DefaultBounds(Double.POSITIVE_INFINITY, 0.0, 1));

        this.relativeDeathRateParameter = relativeDeathRateParameter;
        if( relativeDeathRateParameter != null ) {
          addVariable(relativeDeathRateParameter);
          relativeDeathRateParameter.addBounds(new Parameter.DefaultBounds(1.0, 0.0, 1));
        }

        this.sampleProbability = sampleProbability;
        if (sampleProbability != null) {
            addVariable(sampleProbability);
            sampleProbability.addBounds(new Parameter.DefaultBounds(1.0, 0.0, 1));
        }

        this.conditionalOnRoot = conditionalOnRoot;
        if ( conditionalOnRoot && sampleProbability != null) {
            throw new IllegalArgumentException("Not supported: birth death prior conditional on root with sampling probability.");
        }

        this.type = type;
    }

    @Override
    public boolean isYule() {
        // Yule only
        return (relativeDeathRateParameter == null && sampleProbability == null && !conditionalOnRoot);
    }

    @Override
    public double getMarginal(Tree tree, CalibrationPoints calibration) {
       // Yule only
       return calibration.getCorrection(tree, getR());
    }

    public double getR() {
        return birthDiffRateParameter.getParameterValue(0);
    }

    public double getA() {
        return relativeDeathRateParameter != null ? relativeDeathRateParameter.getParameterValue(0) : 0;
    }

    public double getRho() {
        return sampleProbability != null ? sampleProbability.getParameterValue(0) : 1.0;
    }

    private double logCoeff(int taxonCount) {
        switch( type ) {
            case UNSCALED: break;
            case TIMESONLY: return logGamma(taxonCount + 1);
            case ORIENTED:  return Math.log(taxonCount);
            case LABELED:   {
                final double two2nm1 = (taxonCount - 1) * Math.log(2.0);
                if( ! conditionalOnRoot ) {
                    return two2nm1 - logGamma(taxonCount);
                } else {
                    return two2nm1 - Math.log(taxonCount-1) - logGamma(taxonCount+1);
                }
            }
        }
        return 0.0;
    }

    public double logTreeProbability(int taxonCount) {
        double c1 = logCoeff(taxonCount);
        if( ! conditionalOnRoot ) {
            c1 += (taxonCount - 1) * Math.log(getR() * getRho()) + taxonCount * Math.log(1 - getA());
        }
        return c1;
    }


//    @Override
//    public double logMarginalDensity(int nTaxa, final double h[], int nClade, boolean forParent) {
//
//     }

//    @Override
//    public double logMarginalDensity(int nTaxa, final double h, int nClade, boolean forParent) {
//        double lgp;
//
//        final double lam = getR();
//        final double lh = lam * h;
//
//        if( forParent ) {
//            // n(n+1) factor left out
//
//            lgp = -2 * lh + Math.log(lam);
//            if( nClade > 1 ) {
//                lgp += (nClade-1) * Math.log(1 - Math.exp(-lh));
//            }
//        } else {
//            assert nClade > 1;
//
//            lgp = -3 * lh + (nClade-2) * Math.log(1 - Math.exp(-lh)) + Math.log(lam);
//
//            // root is a special case
//            if( nTaxa == nClade ) {
//                // n(n-1) factor left out
//                lgp += lh;
//            } else {
//                // (n^3-n)/2 factor left out
//            }
//        }
//
//        return lgp;
//    }
//
//    @Override
//    public double logMarginalDensity(final int nTaxa, double h2, final int n, double h1, int nm) {
//
//        assert h2 <= h1 && n < nm;
//
//        final double lam = getR();
//        final int m = nm - n;
//
//        final double elh2 = Math.exp(-lam*h2);
//        final double elh1 = Math.exp(-lam*h1);
//
//        double lgl= 2 * Math.log(lam);
//
//        lgl += (n-2) * Math.log(1-elh2);
//        lgl += (m-3) * Math.log(1-elh1);
//
//        lgl += Math.log(1 - 2*m*elh1 + 2*(m-1)*elh2
//                - m*(m-1)*elh1*elh2 + (m*(m+1)/2.)*elh1*elh1
//                + ((m-1)*(m-2)/2.)*elh2*elh2);
//
//        if( nm < nTaxa ) {
//            /* lgl += Math.log(0.5*(n*(n*n-1))*(n+1+m)) */
//            lgl -= lam*(h2+3*h1);
//        } else {
//            /* lgl += Math.log(lam) /* + Math.log(n*(n*n-1)) */
//            lgl -= lam*(h2+2*h1);
//        }
//
//        return lgl;
//    }

    public double logNodeProbability(Tree tree, NodeRef node) {
        final double height = tree.getNodeHeight(node);
        final double r = getR();
        final double mrh = -r * height;
        final double a = getA();

        if( ! conditionalOnRoot ) {
            final double rho = getRho();
            final double z = Math.log(rho + ((1 - rho) - a) * Math.exp(mrh));
            double l = -2 * z + mrh;

            if( tree.getRoot() == node ) {
                l += mrh - z;
            }
            return l;
        } else {
            double l;
            if( tree.getRoot() != node ) {
                final double z = Math.log(1 - a * Math.exp(mrh));
                l = -2 * z + mrh;
            } else {
                // Root dependent coefficient from each internal node
                final double ca = 1 - a;
                final double emrh = Math.exp(-mrh);
                if( emrh != 1.0 ) {
                  l = (tree.getTaxonCount() - 2) * Math.log(r * ca * (1 + ca /(emrh - 1)));
                } else {  // use exp(x)-1 = x for x near 0
                  l = (tree.getTaxonCount() - 2) * Math.log(ca * (r + ca/height));
                }
            }
            return l;
        }
    }

    public boolean includeExternalNodesInLikelihoodCalculation() {
        return false;
    }
}