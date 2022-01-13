package OOP.Solution;

import OOP.Provided.OOPExpectedException;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

public class OOPExpectedExceptionImpl implements OOPExpectedException {

    private Class exp;
    private LinkedList<String> subMsgs = new LinkedList<String>();

    /**
     * @return the expected exception type.
     */
    public Class getExpectedException() {
        return this.exp;
    }

    /**
     * expect an exception with the given type to be thrown.
     *
     * @param expected - the expected exception type.
     * @return this object.
     */
    public OOPExpectedException expect(Class<? extends Exception> expected) {
        this.exp = expected;
        return this;
    }

    /**
     * expect the exception message to have a message as its substring.
     * Should be okay if the message expected is a substring of the entire exception message.
     * Can expect several messages.
     * Example: for the exception message: "aaa bbb ccc", for an OOPExpectedException e:
     * e.expectMessage("aaa").expectMessage("bb c");
     * - This should be okay.
     *
     * @param msg - the expected message.
     * @return this object.
     */
    public OOPExpectedException expectMessage(String msg) {
        this.subMsgs.add(msg);
        return this;
    }

    /**
     * checks that the exception that was thrown, and passed as parameter,
     * is of a type as expected. Also checks expected message are contained in the exception message.
     * <p>
     * Should handle inheritance. For example, for expected exception A, if an exception B was thrown,
     * and B extends A, then it should be okay.
     *
     * @param e - the exception that was thrown.
     * @return whether or not the actual exception was as expected.
     */
    public boolean assertExpected(Exception e) {
        if(this.exp == null){
            return e == null;
        } else {
            if(e == null) return false;
        }
        Class<? extends Exception> eClass = e.getClass();
        if (!this.exp.isAssignableFrom(eClass)) {
            return false;
        }
        AtomicBoolean contained = new AtomicBoolean(true);
        for(String msg: this.subMsgs) {
            contained.set(
                contained.get() && (e.getMessage() != null && e.getMessage().contains(msg)));
        }
        return contained.get();
    }

    /**
     * @return an instance of an ExpectedException with no exception or expected messages.
     * <p>
     * This static method must be implemented in OOPExpectedExceptionImpl and return an actual object.
     * <p>
     * If this is the state of the ExpectedException object, then no exception is expected to be thrown.
     * So, if an exception is thrown, an OOPResult with ERROR should be returned
     */
    public static OOPExpectedException none() {
        return new OOPExpectedExceptionImpl();
    }

}
