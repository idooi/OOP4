package OOP.Solution;

import OOP.Provided.OOPResult;

/**
 * A class that represents the result of a test.
 */
public class OOPResultImpl implements OOPResult {

    private OOPTestResult resultType;

    /**
     * @return the result type, which is one of four possible type. See OOPTestResult.
     */
    public OOPTestResult getResultType() {
        return resultType;
    }

    /**
     * @return the message of the result in case of an error.
     */
    public String getMessage() {

    }

    /**
     * Equals contract between two test results.
     */
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj.getClass() != this.getClass()) {
            return false;
        }
        OOPResultImpl res = (OOPResultImpl) obj;
        return res.getMessage().equals(this.getMessage()) && res.getResultType().equals(this.getResultType());
    }
}
