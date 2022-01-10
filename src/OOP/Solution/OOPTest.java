package OOP.Solution;

import java.lang.annotation.Target;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static java.lang.annotation.ElementType.METHOD;

@Target(METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OOPTest {

    int order() default 1;

    String tag() default "";
}