package dr.util;

import org.ejml.data.DenseMatrix64F;
import org.ejml.factory.DecompositionFactory;
import org.ejml.interfaces.decomposition.SingularValueDecomposition;
import org.ejml.ops.SingularOps;

public class SVDTransform extends Transform.MultivariateTransform {
    private static final String ORTHO = "orthogonalTransform";

    private final SingularValueDecomposition svd;
    private final DenseMatrix64F inputBuffer;
    private final DenseMatrix64F outputBuffer;
    private final DenseMatrix64F U;


    public SVDTransform(int nRows, int nCols) {
        super(nRows * nCols);
        this.svd = DecompositionFactory.svd(nRows, nCols, true, true, true);
        this.inputBuffer = new DenseMatrix64F(nRows, nCols);
        this.outputBuffer = new DenseMatrix64F(nRows, nCols);
        this.U = new DenseMatrix64F(nRows, nRows);
    }


    @Override
    public double[] inverse(double[] values, int from, int to, double sum) {
        throw new RuntimeException("Cannot invert.");
    }

    @Override
    public double[] gradient(double[] values, int from, int to) {
        throw new RuntimeException("Not implemented.");
    }

    @Override
    public double[] gradientInverse(double[] values, int from, int to) {
        throw new RuntimeException("Not implemented.");
    }

    @Override
    public String getTransformName() {
        return ORTHO;
    }

    @Override
    protected double[] transform(double[] values) {
        inputBuffer.setData(values);
        svd.decompose(inputBuffer);

        double[] singularValues = svd.getSingularValues();
        svd.getV(outputBuffer, true);
        svd.getU(U, false);

        if (!descending(singularValues)) {
            SingularOps.descendingOrder(U, false, singularValues, singularValues.length, outputBuffer, true);
        }
        for (int i = 0; i < outputBuffer.getNumRows(); i++) {
            double sv = singularValues[i];
            for (int j = 0; j < outputBuffer.getNumCols(); j++) {
                outputBuffer.set(i, j, sv * outputBuffer.get(i, j));
            }
        }
        return outputBuffer.getData();
    }

    private boolean descending(double[] values) {
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[i - 1]) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected double[] inverse(double[] values) {
        throw new RuntimeException("Cannot invert.");
    }

    @Override
    protected double getLogJacobian(double[] values) {
        throw new RuntimeException("Not implemented.");
    }

    @Override
    protected double[] getGradientLogJacobianInverse(double[] values) {
        throw new RuntimeException("Not implemented.");
    }

    @Override
    public double[][] computeJacobianMatrixInverse(double[] values) {
        throw new RuntimeException("Not implemented.");
    }

    @Override
    protected boolean isInInteriorDomain(double[] values) {
        throw new RuntimeException("Not implemented.");
    }

    public double[][] getU() {
        double[][] uArray = new double[U.numRows][U.numRows]; //TODO: cached if needed
        for (int i = 0; i < U.numRows; i++) {
            for (int j = 0; j < U.numCols; j++) {
                uArray[i][j] = U.get(i, j);
            }
        }
        return uArray;
    }
}