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
        if (expected == null) {
            if (actual == null) {
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
        if (testClass == null || tag == null) {
            throw new IllegalArgumentException();
        }
        OOPTestClass testClassAnnotation = testClass.getAnnotation(OOPTestClass.class);
        if (testClassAnnotation == null) {
            throw new IllegalArgumentException();
        }
        Constructor<?> ctor = null;
        boolean access = false;
        try {
            ctor = testClass.getDeclaredConstructor();
            access = ctor.isAccessible();
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

        runSetupMethods(instance);
        runTests(instance, tag, summary);
        ctor.setAccessible(access);
        return new OOPTestSummary(summary);
    }

    private static void runTests(Object instance, String tag, Map<String, OOPResult> summary) {
        if (instance == null) {
            return;
        }
        Class<?> testClass = instance.getClass();
        List<Method> testMethods = getMethodList(testClass, OOPTest.class, tag);
        testMethods.sort((t1, t2) -> {
            OOPTestClass oopTestClass1 = t1.getDeclaringClass().getAnnotation(OOPTestClass.class);
            OOPTestClass oopTestClass2 = t2.getDeclaringClass().getAnnotation(OOPTestClass.class);
            if(oopTestClass1 == null && oopTestClass2 == null) {
                return 0;
            }
            if(oopTestClass1 == null) {
                return -1;
            }
            if(oopTestClass2 == null) {
                return 1;
            }
            if (oopTestClass1.value() == oopTestClass2.value()) {
                return 0;
            }
            if (oopTestClass1.value() == OOPTestClass.OOPTestClassType.ORDERED
                && oopTestClass2.value() == OOPTestClass.OOPTestClassType.UNORDERED) {
                return -1;
            }
            if (oopTestClass1.value() == OOPTestClass.OOPTestClassType.UNORDERED
                && oopTestClass2.value() == OOPTestClass.OOPTestClassType.ORDERED) {
                return 1;
            }
            return 0;
        });
        testMethods.sort((test1, test2) -> {
            OOPTest oopTest1 = test1.getAnnotation(OOPTest.class);
            OOPTest oopTest2 = test2.getAnnotation(OOPTest.class);
            return oopTest1.order() - oopTest2.order();
        });
        for (Method test : testMethods) {
            runTest(test, testClass, instance, summary);
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

        AtomicReference<Object> backup = new AtomicReference<>(backUp(instance));
        List<Method> beforeMethods = getMethodList(testClass, OOPBefore.class, test.getName());
        for (Method method : beforeMethods) {
            try {
                backup.set(backUp(instance));
                runFunction(instance, method);
            } catch (IllegalAccessException | InvocationTargetException e) {
                restore(instance, backup.get());
                summary.put(test.getName(), new OOPResultImpl(OOPResult.OOPTestResult.ERROR, e.getMessage()));
                return;
            }
        }
        try {
            runFunction(instance, test);
            boolean summarySet = false;
            if (expectedExceptionField != null) {
                OOPExpectedException exceptionWrapper = (OOPExpectedException) expectedExceptionField.get(instance);
                if (exceptionWrapper.getExpectedException() != null) {
                    summary.put(test.getName(), new OOPResultImpl(OOPResult.OOPTestResult.ERROR,
                        exceptionWrapper.getExpectedException().toString()));
                    summarySet = true;
                }
            }
            if (!summarySet) {
                summary.put(test.getName(), new OOPResultImpl(OOPResult.OOPTestResult.SUCCESS, null));
            }

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
                        if (expectedExceptionField != null) {
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

        List<Method> afterMethods = getMethodList(testClass, OOPAfter.class, test.getName());
        for (Method method : afterMethods) {
            try {
                backup.set(backUp(instance));
                runFunction(instance, method);
            } catch (IllegalAccessException | InvocationTargetException e) {
                restore(instance, backup.get());
                summary.replace(test.getName(), new OOPResultImpl(OOPResult.OOPTestResult.ERROR, e.getMessage()));
            }
        }

    }

    private static void runFunction(Object instance, Method method)
        throws IllegalAccessException, InvocationTargetException {
        boolean access = method.isAccessible();
        method.setAccessible(true);
        method.invoke(instance);
        method.setAccessible(access);
    }

    private static void restore(Object destination, Object source) {
        try {
            Field[] fields = destination.getClass().getDeclaredFields();
            for (Field f : fields) {
                boolean access = f.isAccessible();
                f.setAccessible(true);
                f.set(destination, f.get(source));
                f.setAccessible(access);
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
        boolean access = f.isAccessible();
        f.setAccessible(true);
        Object value = f.get(input);
        try {
            Method clone = value.getClass().getDeclaredMethod("clone");
            boolean cloneAccess = clone.isAccessible();
            clone.setAccessible(true);
            f.set(output, clone.invoke(value));
            clone.setAccessible(cloneAccess);
        } catch (Exception canNotClone) {
            try {
                Constructor<?> constructorMethod = value.getClass().getDeclaredConstructor(f.getType());
                boolean ctorAccess = constructorMethod.isAccessible();
                constructorMethod.setAccessible(true);
                constructorMethod.setAccessible(ctorAccess);
                f.set(output, constructorMethod.newInstance(value));
            } catch (Exception canNotConstruct) {
                f.set(output, value);
            }
        }
        f.setAccessible(access);
    }

    private static void runSetupMethods(Object instance) {
        if(instance == null) return;
        List<Method> setupMethods = getMethodList(instance.getClass(), OOPSetup.class);
        if (setupMethods.size() >= 1) {
            SortByClassHiearchy(setupMethods);
        } else {
            return;
        }
        for (Method setup : setupMethods) {
            assertTrue(setup.getParameterTypes().length == 0);
            try {
                runFunction(instance, setup);

            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();

            }
        }
    }

    private static List<Method> getMethodIfExistsInSuperclass(Method setup) {
        Class<?> subClass = setup.getDeclaringClass();
        Class<?> superClass = subClass.getSuperclass();
        List<Method> methods = new LinkedList<Method>();
        while (superClass != null && superClass != Object.class) {
            try {
                Method temp = superClass.getDeclaredMethod(setup.getName());
                if (temp.isAnnotationPresent(OOPSetup.class)) {
                    methods.add(temp);
                }
            } catch (NoSuchMethodException ignore) {
            }
            superClass = superClass.getSuperclass();
        }
        Collections.reverse(methods);
        return methods;
    }

    private static void SortByClassHiearchy(List<Method> setupMethods) {
        setupMethods.sort((s1, s2) -> {
            Class<?> s1Class = s1.getDeclaringClass();
            Class<?> s2Class = s2.getDeclaringClass();
            if (s1Class.equals(s2Class)) {
                return 0;
            }
            if (s1Class.isAssignableFrom(s2Class)) {
                return -1;
            } else if (s2Class.isAssignableFrom(s1Class)) {
                return 1;
            }
            // no hierarchy
            return 0;
        });
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
        for (Method m : methods) {
            if (c == OOPTest.class) {

                OOPTest ann = (OOPTest) m.getAnnotation(c);
                if (ann != null && (tag.equals("") || ann.tag().equals(tag))) {
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
        SortByClassHiearchy(out);
        if (c == OOPAfter.class) {
            Collections.reverse(out);
        }
        return out;
    }

    private static List<Method> getMethodList(Class<?> testClass, Class<? extends Annotation> c) {
        return Arrays.stream(testClass.getMethods()).filter(m -> m.isAnnotationPresent(c)).collect(Collectors.toList());
    }

    public static OOPTestSummary runClass(Class<?> testClass) {
        return runClass(testClass, "");
    }

}
