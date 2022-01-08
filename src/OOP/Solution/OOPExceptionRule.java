package OOP.Solution;

import OOP.Provided.OOPExpectedException;
import java.lang.annotation.Target;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static java.lang.annotation.ElementType.METHOD;

@Target(OOPExpectedException)
@Retention(RetentionPolicy.RUNTIME)
public @interface OOPExceptionRule {
}