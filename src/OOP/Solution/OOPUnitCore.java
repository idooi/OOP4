package OOP.Solution;

import OOP.Provided.OOPAssertionFailure;
import OOP.Provided.OOPExpectedException;
import OOP.Provided.OOPResult;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class OOPUnitCore {

    public static void assertEquals(Object expected, Object actual) throws OOPAssertionFailure {
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
        OOPTestClass testClassAnnotation = testClass.getAnnotation(OOPTestClass.class);
        if (testClassAnnotation == null) {
            throw new IllegalArgumentException();
        }
        Constructor<?> ctor = null;
        try {
            ctor = testClass.getDeclaredConstructor();
        } catch (NoSuchMethodException ignored) {
        }

        Object instance = null;
        try {
            assert ctor != null;
            instance = ctor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }

        // testClass, parent of testClass, parent of parent of testClass...
        List<Class<?>> allClasses = getSuperClassList(testClass);

        runSetupMethods(allClasses, instance);
        runTestsRecursize(allClasses, instance, tag);

        List<Method> beforeMethods = getMethodList(testClass, OOPBefore.class);

        List<Method> afterMethods = getMethodList(testClass, OOPAfter.class);

        List<Method> testMethods = getMethodList(testClass, OOPTest.class);
    }

    private static void runTestsRecursize(List<Class<?>> allClasses, Object instance, String tag) {
        if (allClasses.size() == 0) {
            return;
        }
        List<Class<?>> allClassesReversed = allClasses;
        Collections.reverse(allClassesReversed);
        allClassesReversed.forEach(c -> {
            runTests(c, instance, tag);
        });
    }

    private static void runTests(Class<?> testClass, Object instance, String tag) {
        List<Method> testMethods = getMethodList(testClass, OOPTest.class);
        OOPTestClass oopTestClass = testClass.getAnnotation(OOPTestClass.class);
        assert oopTestClass != null;
        if (oopTestClass.value() == OOPTestClass.OOPTestClassType.ORDERED) {
            testMethods.stream().sorted((test1, test2) -> {
                OOPTest oopTest1 = test1.getAnnotation(OOPTest.class);
                OOPTest oopTest2 = test2.getAnnotation(OOPTest.class);
                return oopTest1.order() - oopTest2.order();
            }).collect(Collectors.toList()).forEach(test -> {
                runTest(test, testClass, instance, tag);
            });
        } else { //UNORDERED
            testMethods.forEach(test -> {
                runTest(test, testClass, instance, tag);
            });
        }

    }

    private static void runTest(Method test, Class<?> testClass, Object instance, String tag) {
        AtomicReference<OOPResult.OOPTestResult> result = new AtomicReference<>(OOPResult.OOPTestResult.SUCCESS);
        Field expectedExceptionField = findFields(testClass, OOPExceptionRule.class);
        OOPExpectedException oopExpectedException = null;
        if(expectedExceptionField != null) {

        }

        } String testName = test.getName();
        List<Class<?>> allClasses = getSuperClassList(testClass);
        List<Class<?>> allClassesReversed = allClasses;
        Collections.reverse(allClassesReversed);
        //TODO: implement backup of instance in case before method throws exception
        allClassesReversed.forEach(c -> {
            List<Method> beforeMethods = getMethodList(c, OOPBefore.class, tag);
            beforeMethods.forEach(method -> {
                try {
                    method.invoke(instance);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    result.set(OOPResult.OOPTestResult.ERROR);
                }
            });
        });
        try {
            test.invoke(instance);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        allClasses.forEach(c -> {
            List<Method> beforeMethods = getMethodList(c, OOPAfter.class, tag);
            beforeMethods.forEach(method -> {
                try {
                    method.invoke(instance);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    result.set(OOPResult.OOPTestResult.ERROR);
                }
            });
        });

    }

    private static void runSetupMethods(List<Class<?>> allClasses, Object instance) {
        allClasses.forEach(c -> {
            List<Method> setupMethods = getMethodList(c, OOPSetup.class);
            setupMethods.forEach(setup -> {
                assertTrue(setup.getParameterTypes().length == 0);
                try {
                    setup.invoke(instance);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            });
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
        assert c == OOPBefore.class || c == OOPAfter.class;
        return Arrays.stream(testClass.getMethods()).filter(m -> {
            if (c == OOPBefore.class) {
                OOPBefore ann = (OOPBefore) m.getAnnotation(c);
                return Arrays.asList(ann.value()).contains(tag);
            }
            OOPAfter ann = (OOPAfter) m.getAnnotation(c);
            return Arrays.asList(ann.value()).contains(tag);
        }).collect(Collectors.toList());
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
        while (superclass != null) {
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
