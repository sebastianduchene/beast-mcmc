/*
 * TreeTipGradient.java
 *
 * Copyright (c) 2002-2017 Alexei Drummond, Andrew Rambaut and Marc Suchard
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
 *  BEAST is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package dr.evomodel.treedatalikelihood.hmc;

import dr.inference.hmc.GradientWrtParameterProvider;
import dr.inference.model.Likelihood;
import dr.inference.model.Parameter;
import dr.math.distributions.WishartSufficientStatistics;
import dr.math.interfaces.ConjugateWishartStatisticsProvider;
import dr.xml.Reportable;

/**
 * @author Paul Bastide
 * @author Marc A. Suchard
 */
public class PrecisionGradient implements GradientWrtParameterProvider, Reportable {

    private final ConjugateWishartStatisticsProvider wishartStatistics;
    private final Likelihood likelihood;
    private final Parameter parameter;


    public PrecisionGradient(ConjugateWishartStatisticsProvider wishartStatistics,
                             Likelihood likelihood,
                             Parameter parameter) {

        this.wishartStatistics = wishartStatistics;
        this.likelihood = likelihood;
        this.parameter = parameter;
    }

    @Override
    public Likelihood getLikelihood() {
        return likelihood;
    }

    @Override
    public Parameter getParameter() {
        return parameter;
    }

    @Override
    public int getDimension() {
        return getParameter().getDimension();
    }
    
    @Override
    public double[] getGradientLogDensity() {

        double[] gradient = new double[getDimension()];

        // TODO I believe we need:
        // TODO (1) d log det(parameter) -- easy if parameter is a function of a triangular decomposition
        // TODO (2) weightedSumOfSquares = Y' \Phi Y, where Y = fully observed / sampled tip trait matrix and \Phi is tree-precision

        WishartSufficientStatistics statistics = wishartStatistics.getWishartStatistics();
        double[] weightedSumOfSquares = statistics.getScaleMatrix();
        int numberBranches = statistics.getDf();

        // TODO Compute w.r.t. to precision
        // TODO Chain-rule w.r.t. to parametrization

        return gradient;
    }

    @Override
    public String getReport() {
        return (new dr.math.matrixAlgebra.Vector(getGradientLogDensity())).toString();
    }
}
