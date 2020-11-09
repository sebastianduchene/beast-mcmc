/*
 * DiscreteTraitBranchRateGradient.java
 *
 * Copyright (c) 2002-2020 Alexei Drummond, Andrew Rambaut and Marc Suchard
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

package dr.evomodel.treedatalikelihood.discrete;

import dr.evolution.tree.Tree;
import dr.evolution.tree.TreeTrait;
import dr.evolution.tree.TreeTraitProvider;
import dr.evomodel.substmodel.OldGLMSubstitutionModel;
import dr.evomodel.treedatalikelihood.BeagleDataLikelihoodDelegate;
import dr.evomodel.treedatalikelihood.ProcessSimulation;
import dr.evomodel.treedatalikelihood.TreeDataLikelihood;
import dr.evomodel.treedatalikelihood.preorder.ProcessSimulationDelegate;
import dr.inference.distribution.GeneralizedLinearModel;
import dr.inference.hmc.GradientWrtParameterProvider;
import dr.inference.loggers.LogColumn;
import dr.inference.loggers.Loggable;
import dr.inference.model.CompoundParameter;
import dr.inference.model.Likelihood;
import dr.inference.model.Parameter;
import dr.inference.operators.hmc.HamiltonianMonteCarloOperator;
import dr.math.matrixAlgebra.WrappedVector;
import dr.util.Author;
import dr.util.Citable;
import dr.util.Citation;
import dr.xml.Reportable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Marc A. Suchard
 */
public class GlmSubstitutionModelGradient implements GradientWrtParameterProvider, Reportable, Loggable, Citable {

    protected final TreeDataLikelihood treeDataLikelihood;
    protected final TreeTrait treeTraitProvider;
    protected final Tree tree;

    protected final OldGLMSubstitutionModel substitutionModel;
    protected final GeneralizedLinearModel glm;
//    protected final Parameter allCoefficients;
    protected final ParameterMap parameterMap;
    protected final int stateCount;

    interface ParameterMap {
        double[] getCovariateColumn(int i);
        Parameter getParameter();
    }



    public GlmSubstitutionModelGradient(String traitName,
                                        TreeDataLikelihood treeDataLikelihood,
                                        BeagleDataLikelihoodDelegate likelihoodDelegate,
                                        OldGLMSubstitutionModel substitutionModel) {

        this.treeDataLikelihood = treeDataLikelihood;
        this.tree = treeDataLikelihood.getTree();
        this.substitutionModel = substitutionModel;
        this.glm = substitutionModel.getGeneralizedLinearModel();
//        this.allCoefficients = makeCompoundParameter(glm);
        this.parameterMap = makeParameterMap(glm);
        this.stateCount = substitutionModel.getDataType().getStateCount();

        String name = SubstitutionModelCrossProductDelegate.getName(traitName);
        TreeTrait test = treeDataLikelihood.getTreeTrait(name);

        if (test == null) {
            ProcessSimulationDelegate gradientDelegate = new SubstitutionModelCrossProductDelegate(traitName,
                    treeDataLikelihood.getTree(),
                    likelihoodDelegate,
                    treeDataLikelihood.getBranchRateModel(),
                    substitutionModel.getDataType().getStateCount());
            TreeTraitProvider traitProvider = new ProcessSimulation(treeDataLikelihood, gradientDelegate);
            treeDataLikelihood.addTraits(traitProvider.getTreeTraits());
        }

        treeTraitProvider = treeDataLikelihood.getTreeTrait(name);
        assert (treeTraitProvider != null);
    }

    ParameterMap makeParameterMap(GeneralizedLinearModel glm) {

        final List<Integer> whichBlock = new ArrayList<>();
        final List<Integer> whichIndex = new ArrayList<>();

        CompoundParameter cp = new CompoundParameter("fixedEffects");
        boolean multi = glm.getNumberOfFixedEffects() > 1;

        for (int i = 0; i < glm.getNumberOfFixedEffects(); ++i) {
            Parameter p = glm.getFixedEffect(i);
            if (multi) {
                cp.addParameter(p);
            }
            for (int j = 0; j < p.getDimension(); ++j) {
                whichBlock.add(i);
                whichIndex.add(j);
            }
        }

        final Parameter whichParameter = multi ? cp : glm.getFixedEffect(0);
        return new ParameterMap() {

            public double[] getCovariateColumn(int i) {
                return glm.getDesignMatrix(whichBlock.get(i)).getColumnValues(whichIndex.get(i));
            }

            public Parameter getParameter() { return whichParameter; }
        };
    }

    Parameter makeCompoundParameter(GeneralizedLinearModel glm) {
        CompoundParameter parameter = new CompoundParameter("test");
        for (int i = 0; i < glm.getNumberOfFixedEffects(); ++i) {
            parameter.addParameter(glm.getFixedEffect(i));
        }
        return parameter;
    }

    @Override
    public Likelihood getLikelihood() {
        return treeDataLikelihood;
    }

    @Override
    public Parameter getParameter() {
        return parameterMap.getParameter();
    }

    @Override
    public int getDimension() {
        return getParameter().getDimension();
    }

    @Override
    public double[] getGradientLogDensity() {

        long startTime;
        if (COUNT_TOTAL_OPERATIONS) {
            startTime = System.nanoTime();
        }

        double[] differentials = (double[]) treeTraitProvider.getTrait(tree, null);
        double[] generator = new double[differentials.length];

        substitutionModel.getInfinitesimalMatrix(generator);
        double[] pi = substitutionModel.getFrequencyModel().getFrequencies();

        double normalizationConstant = preProcessNormalization(differentials, generator,
                substitutionModel.getNormalization());

        final double[] gradient = new double[getParameter().getDimension()];
        for (int i = 0; i < getParameter().getDimension(); ++i) {
            gradient[i] = processSingleGradientDimension(i, differentials, generator, pi,
                    substitutionModel.getNormalization(),
                    normalizationConstant);
        }

        if (COUNT_TOTAL_OPERATIONS) {
            ++gradientCount;
            long endTime = System.nanoTime();
            totalGradientTime += (endTime - startTime) / 1000000;
        }

        return gradient;
    }

    double preProcessNormalization(double[] differentials, double[] generator,
                                   boolean normalize) {
        return 0.0;
    }

    double processSingleGradientDimension(int i,
                                          double[] differentials, double[] generator, double[] pi,
                                          boolean normalize, double normalizationConstant) {
//        DesignMatrix designMatrix = glm.getDesignMatrix(i);
//        double[] covariate = designMatrix.getColumnValues(0);

        double[] covariate = parameterMap.getCovariateColumn(i);

        return calculateCovariateDifferential(generator, differentials, covariate, pi, normalize);
    }

//    private double[] transposeOffDiagonal(double[] x) {
//        double[] result = new double[x.length];
//        int half = x.length / 2;
//        System.arraycopy(x, half, result, 0, half);
//        System.arraycopy(x, 0, result, half, half);
//
//        return result;
//    }
//
//    private double[] transposeAll(double[] x) {
//        double[] result = new double[stateCount * stateCount];
//
//        for (int i = 0; i < stateCount; ++i) {
//            for (int j = 0; j < stateCount; ++j) {
//                result[index(j,i)] = x[index(i,j)];
//            }
//        }
//
//        return result;
//    }

    private double calculateCovariateDifferential(double[] generator, double[] differential,
                                                  double[] covariate, double[] pi,
                                                  boolean doNormalization) {

        double normalization = 0.0;
        double total = 0.0;

        int k = 0;
        for (int i = 0; i < stateCount; ++i) {
            for (int j = i + 1; j < stateCount; ++j) {

                double xij = covariate[k++];
                double element = xij * generator[index(i,j)];

                total += differential[index(i,j)] * element;
                total -= differential[index(i,i)] * element;

                normalization += element * pi[i];
            }
        }

        for (int j = 0; j < stateCount; ++j) {
            for (int i = j + 1; i < stateCount; ++i) {

                double xij = covariate[k++];
                double element = xij * generator[index(i,j)];

                total += differential[index(i,j)] * element;
                total -= differential[index(i,i)] * element;

                normalization += element * pi[i];
            }
        }

        if (doNormalization) {
            for (int i = 0; i < stateCount; ++i) {
                for (int j = 0; j < stateCount; ++j) {
                    total -= differential[index(i,j)] * generator[index(i,j)] * normalization;
                }
            }
        }

        return total;
    }

    int index(int i, int j) {
        return i * stateCount + j;
    }

//    private int idx(int i, int j) {
//        if (j > i) {
//            return
//        }
//    }

    private double calculateNormalizationDifferential(double[] generator, double[] covariate, double[] pi) {

        double total = 0.0;

        int k = 0;
        for (int i = 0; i < stateCount; ++i) {
            for (int j = i + 1; j < stateCount; ++j) {
                double xij = covariate[k++];
                total += xij * generator[i * stateCount + j] * pi[i];
            }
        }

        for (int j = 0; j < stateCount; ++j) {
            for (int i = j + 1; i < stateCount; ++i) {
                double xij = covariate[k++];
                total += xij * generator[i * stateCount + j] * pi[i];
            }
        }

        return total;
    }

    private static double dotProduct(double[] x, double[] y) {
        assert x.length == y.length;

        double total = 0.0;
        for (int i = 0; i < x.length; ++i) {
            total += x[i] * y[i];
        }

        return total;
    }


    @Override
    public String getReport() {

        StringBuilder sb = new StringBuilder();
        if (COUNT_TOTAL_OPERATIONS) {
            sb.append("\n\tgetCrossProductGradientCount = ").append(gradientCount);
            sb.append("\n\taverageGradientTime = ");
            if (gradientCount > 0) {
                sb.append(totalGradientTime / gradientCount);
            } else {
                sb.append("NA");
            }
            sb.append("\n");
        }

        String message = GradientWrtParameterProvider.getReportAndCheckForError(this, 0.0, Double.POSITIVE_INFINITY, null);
        sb.append(message);

        return  sb.toString();
    }

    private static final boolean COUNT_TOTAL_OPERATIONS = true;
    private long gradientCount = 0;
    private long totalGradientTime = 0;

    @Override
    public LogColumn[] getColumns() {
        return Loggable.getColumnsFromReport(this, "gradient report");
    }

    @Override
    public Citation.Category getCategory() {
        return Citation.Category.FRAMEWORK;
    }

    @Override
    public String getDescription() {
        return "Using linear-time differential calculations for all substitution generator elements";
    }

    @Override
    public List<Citation> getCitations() {
        return Collections.singletonList(CITATION);
    }

    private static final Citation CITATION = new Citation(
            new Author[]{
                    new Author("P", "Lemey"),
                    new Author("MA", "Suchard"),
            },
            "Phylogeographic GLM random effects",
            "",
            Citation.Status.IN_PREPARATION);

    private static final boolean RETHROW_DECOMPOSITION_ERROR = true;
}
