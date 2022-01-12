package OOP.Solution;

import OOP.Provided.OOPAssertionFailure;
import OOP.Provided.OOPExceptionMismatchError;
import OOP.Provided.OOPExpectedException;
import OOP.Provided.OOPResult;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class OOPUnitCore {

    public static void assertEquals(Object expected, Object actual) throws OOPAssertionFailure {
        if(expected == null){
            if(actual == null){
                return;
            } else {
                throw new OOPAssertionFailure(expected, actual);
            }
        }
        if (!expected.equals(actual)) {
            throw new OOPAssertionFailure(expected, actual);
        }
    }

    public static void assertTrue(Object o) throws OOPAssertionFailure {
        assertEquals(o, true);
    }

    public static void assertFalse(Object o) throws OOPAssertionFailure {
        assertEquals(o, false);
    }

    public static void fail() throws OOPAssertionFailure {
        throw new OOPAssertionFailure();
    }

    public static OOPTestSummary runClass(Class<?> testClass, String tag) {
        if (testClass == null) {
            throw new IllegalArgumentException();
        }
        OOPTestClass testClassAnnotation = testClass.getAnnotation(OOPTestClass.class);
        if (testClassAnnotation == null) {
            throw new IllegalArgumentException();
        }
        Constructor<?> ctor = null;
        try {
            ctor = testClass.getDeclaredConstructor();
            ctor.setAccessible(true);
        } catch (NoSuchMethodException ignored) {
        }

        Object instance = null;
        try {
            assert ctor != null;
            instance = ctor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        Map<String, OOPResult> summary = new HashMap<>();

        // testClass, parent of testClass, parent of parent of testClass...
        List<Class<?>> allClasses = getSuperClassList(testClass);

        runSetupMethods(allClasses, instance);
        runTestsRecursize(allClasses, instance, tag, summary);

        return new OOPTestSummary(summary);
    }

    private static void runTestsRecursize(List<Class<?>> allClasses, Object instance, String tag,
        Map<String, OOPResult> summary) {
        if (allClasses.size() == 0) {
            return;
        }
        Collections.reverse(allClasses);
        for (Class<?> c : allClasses) {
            runTests(c, instance, tag, summary);
        }
    }

    private static void runTests(Class<?> testClass, Object instance, String tag, Map<String, OOPResult> summary) {
        List<Method> testMethods = getMethodList(instance.getClass(), OOPTest.class, tag);
        OOPTestClass oopTestClass = testClass.getAnnotation(OOPTestClass.class);
        if (oopTestClass == null) {
            return;
        }
        if (oopTestClass.value() == OOPTestClass.OOPTestClassType.ORDERED) {
            List<Method> sorted = testMethods.stream().sorted((test1, test2) -> {
                OOPTest oopTest1 = test1.getAnnotation(OOPTest.class);
                OOPTest oopTest2 = test2.getAnnotation(OOPTest.class);
                return oopTest1.order() - oopTest2.order();
            }).collect(Collectors.toList());
            for (Method test : sorted) {
                runTest(test, testClass, instance, summary);
            }
        } else { //UNORDERED
            for (Method test : testMethods) {
                runTest(test, testClass, instance, summary);
            }
        }

    }

    private static void runTest(Method test, Class<?> testClass, Object instance, Map<String, OOPResult> summary) {
        Field expectedExceptionField = findFields(testClass, OOPExceptionRule.class);
        OOPExpectedException oopExpectedException;
        if (expectedExceptionField != null) {
            expectedExceptionField.setAccessible(true);
            try {
                expectedExceptionField.set(instance, new OOPExpectedExceptionImpl());
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        List<Class<?>> allClasses = getSuperClassList(testClass);
        List<Class<?>> allClassesReversed = allClasses;
        Collections.reverse(allClassesReversed);
        AtomicReference<Object> backup = new AtomicReference<>(backUp(instance));
        for (Class<?> c : allClassesReversed) {
            List<Method> beforeMethods = getMethodList(c, OOPBefore.class, test.getName());
            for (Method method : beforeMethods) {
                try {
                    backup.set(backUp(instance));
                    method.setAccessible(true);
                    method.invoke(instance);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    restore(instance, backup);
                    summary.put(method.getName(), new OOPResultImpl(OOPResult.OOPTestResult.ERROR, e.getMessage()));
                }
            }
        }
        try {
            test.setAccessible(true);
            test.invoke(instance);

            if (expectedExceptionField != null) {
                OOPExpectedException exceptionWrapper = (OOPExpectedException) expectedExceptionField.get(instance);
                if (exceptionWrapper.getExpectedException() != null) {
                    summary.put(test.getName(),
                        new OOPResultImpl(OOPResult.OOPTestResult.ERROR, exceptionWrapper.getClass().toString()));
                    //                    continue;
                }
            }
            summary.put(test.getName(), new OOPResultImpl(OOPResult.OOPTestResult.SUCCESS, null));
        } catch (IllegalAccessException | InvocationTargetException e) {
            try {
                throw e.getCause();
            } catch (OOPAssertionFailure assertionFailure) {
                summary.put(test.getName(),
                    new OOPResultImpl(OOPResult.OOPTestResult.FAILURE, assertionFailure.getMessage()));
            } catch (Exception exception) {
                //we have caught a generic exception, check if it is expected by the class who DECLARED the method! not neccesarily testclass!!!
                if (Arrays.stream(test.getDeclaringClass().getDeclaredFields()).anyMatch(
                    f -> f.getType().equals(OOPExpectedException.class))) { //there is an expected exception
                    //get the expected exception field value
                    OOPExpectedException exceptionWrapper = null;
                    try {
                        if(expectedExceptionField != null){
                            exceptionWrapper = (OOPExpectedException) expectedExceptionField.get(instance);
                        } else {
                            summary.put(test.getName(),
                                new OOPResultImpl(OOPResult.OOPTestResult.ERROR, e.getClass().toString()));
                            return;
                        }
                    } catch (IllegalAccessException illegalAccessException) {
                        illegalAccessException.printStackTrace();
                    }

                    //check if the expected exception matches the one caught. and update summary accordingly.
                    assert exceptionWrapper != null;
                    if (exceptionWrapper.assertExpected(exception)) {
                        summary.put(test.getName(), new OOPResultImpl(OOPResult.OOPTestResult.SUCCESS, null));

                    } else {
                        if (exceptionWrapper.getExpectedException() == null) {
                            //if no exception was expected and one was thrown(edge case)
                            summary.put(test.getName(),
                                new OOPResultImpl(OOPResult.OOPTestResult.ERROR, e.getClass().toString()));
                        } else {
                            // the expected exception and the caught are mismatched.
                            OOPExceptionMismatchError err = new OOPExceptionMismatchError(
                                exceptionWrapper.getExpectedException(), e.getClass());
                            summary.put(test.getName(),
                                new OOPResultImpl(OOPResult.OOPTestResult.EXPECTED_EXCEPTION_MISMATCH,
                                    err.getMessage()));
                        }
                    }
                } else { //if no exception was expected and one was thrown(general case)
                    summary
                        .put(test.getName(), new OOPResultImpl(OOPResult.OOPTestResult.ERROR, e.getClass().toString()));
                }
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
        for (Class<?> c : allClasses) {
            List<Method> afterMethods = getMethodList(c, OOPAfter.class, test.getName());
            for (Method method : afterMethods) {
                try {
                    backup.set(backUp(instance));
                    method.setAccessible(true);
                    method.invoke(instance);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    restore(instance, backup);
                    summary.put(method.getName(), new OOPResultImpl(OOPResult.OOPTestResult.ERROR, e.getMessage()));
                }
            }
        }
    }

    private static void restore(Object destination, Object source) {
        try {
            Field[] fields = destination.getClass().getDeclaredFields();
            for (Field f : fields) {
                f.setAccessible(true);
                f.set(destination, f.get(source));
            }
        } catch (Exception ignored) {
        }
    }

    private static Object backUp(Object input) {
        //try to construct a new instance of input's type. and clone its fields exactly as stated in the homework.
        try {
            Object output = input.getClass().getConstructor().newInstance();
            Field[] fields = input.getClass().getDeclaredFields();
            for (Field f : fields) {
                copyField(f, output, input);
            }
            return output;
        } catch (Exception constructorException) {
            return null;
        }
    }

    private static void copyField(Field f, Object output, Object input) throws IllegalAccessException {
        f.setAccessible(true);
        Object value = f.get(input);
        try {
            Method clone = value.getClass().getDeclaredMethod("clone");
            clone.setAccessible(true);
            f.set(output, clone.invoke(value));
        } catch (Exception canNotClone) {
            try {
                Constructor<?> constructorMethod = value.getClass().getDeclaredConstructor(f.getType());
                constructorMethod.setAccessible(true);
                f.set(output, constructorMethod.newInstance(value));
            } catch (Exception canNotConstruct) {
                f.set(output, value);
            }
        }
    }

    private static void runSetupMethods(List<Class<?>> allClasses, Object instance) {
        Collections.reverse(allClasses);
        for (Class<?> c : allClasses) {
            List<Method> setupMethods = getMethodList(c, OOPSetup.class);
            for (Method setup : setupMethods) {
                assertTrue(setup.getParameterTypes().length == 0);
                try {
                    setup.setAccessible(true);
                    setup.invoke(instance);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static Field findFields(Class<?> c, Class<? extends Annotation> ann) {
        while (c != null) {
            for (Field field : c.getDeclaredFields()) {
                if (field.isAnnotationPresent(ann)) {
                    return field;
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static List<Method> getMethodList(Class<?> testClass, Class<? extends Annotation> c, String tag) {
        assert c == OOPBefore.class || c == OOPAfter.class || c == OOPTest.class;
        List<Method> methods = new LinkedList<Method>(Arrays.asList(testClass.getMethods()));
        List<Method> out = new LinkedList<Method>();
        for(Method m: methods) {
            if (c == OOPTest.class) {

                OOPTest ann = (OOPTest) m.getAnnotation(c);
                if (ann != null && (tag == null || ann.tag().equals(tag))) {
                    out.add(m);
                    continue;
                } else {
                    continue;
                }
            }
            if (c == OOPBefore.class) {
                OOPBefore ann = (OOPBefore) m.getAnnotation(c);
                if (ann != null && Arrays.asList(ann.value()).contains(tag)) {
                    out.add(m);
                    continue;
                } else {
                    continue;
                }
            }
            OOPAfter ann = (OOPAfter) m.getAnnotation(c);
            if (ann != null && Arrays.asList(ann.value()).contains(tag)) {
                out.add(m);
            }
        }
        return out;
    }

    private static List<Method> getMethodList(Class<?> testClass, Class<? extends Annotation> c) {
        return Arrays.stream(testClass.getMethods()).filter(m -> m.isAnnotationPresent(c)).collect(Collectors.toList());
    }

    /**
     * Returns a List of super classes sorted from the class itself to the first ancestor (direct parent) to the last one
     */
    private static List<Class<?>> getSuperClassList(Class<?> subclass) {
        LinkedList<Class<?>> superClasses = new LinkedList<Class<?>>();
        superClasses.add(subclass);
        Class<?> superclass = subclass.getSuperclass();
        while (superclass != null && superclass != Object.class) {
            superClasses.add(superclass);
            subclass = superclass;
            superclass = subclass.getSuperclass();
        }
        return superClasses;
    }

    public static OOPTestSummary runClass(Class<?> testClass) {
        return runClass(testClass, null);
    }

}
