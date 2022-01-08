package OOP.Solution;

import OOP.Provided.OOPAssertionFailure;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class OOPUnitCore {
    public static void assertEquals(Object expected, Object actual) throws OOPAssertionFailure{
        if (!expected.equals(actual)){
            throw new OOPAssertionFailure(expected, actual);
        }
    }

    public static void fail() throws OOPAssertionFailure{
        throw new OOPAssertionFailure();
    }
    public static OOPTestSummary runClass(Class<?> testClass, String tag){
        Constructor<?> ctor =  testClass.getConstructor();
        ctor

        List<Method> beforeMethods = Arrays.stream(testClass.getMethods()).filter(m -> m.isAnnotationPresent(OOPBefore.class)).collect(Collectors.toList());
    }

}
