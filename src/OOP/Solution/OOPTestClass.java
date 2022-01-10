package OOP.Solution;

import java.lang.annotation.Target;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static java.lang.annotation.ElementType.TYPE;

@Target(TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface OOPTestClass {
    OOPTestClassType value() default OOPTestClassType.UNORDERED;

    enum OOPTestClassType {
        ORDERED, UNORDERED;

    }

}