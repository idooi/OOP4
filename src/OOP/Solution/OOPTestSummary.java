package OOP.Solution;

import OOP.Provided.OOPResult;
import OOP.Provided.OOPResult.OOPTestResult;

import java.util.Map;

public class OOPTestSummary {

    private Map<String, OOPResult> testMap;

    public OOPTestSummary(Map<String, OOPResult> testMap) {
        this.testMap = testMap;
    }

    private int getNum(OOPTestResult resType) {
        return (int) this.testMap.values().stream().filter(res -> res.getResultType().equals(resType)).count();
    }

    public int getNumSuccesses() {
        return getNum(OOPTestResult.SUCCESS);
    }

    public int getNumFailures() {
        return getNum(OOPTestResult.FAILURE);
    }

    public int getNumExceptionMismatches() {
        return getNum(OOPTestResult.EXPECTED_EXCEPTION_MISMATCH);
    }

    public int getNumErrors() {
        return getNum(OOPTestResult.ERROR);
    }
}
