package sun.jvm.hotspot.oops;

import java.io.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.Observable;
import sun.jvm.hotspot.utilities.Observer;

public class Inline extends Instance {

    private final InlineKlass klass;

    static {
        VM.registerVMInitializedObserver((o, d) -> initialize(VM.getVM().getTypeDataBase()));
    }

    private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
        // TODO
        //Type type = db.lookupType("instanceOopDesc");
    }

    Inline(OopHandle handle, ObjectHeap heap, InlineKlass klass) {
        super(handle, heap);
        this.klass = klass;
    }

    Inline(OopHandle handle, ObjectHeap heap) {
        this(handle, heap, null);
    }

    @Override
    public boolean isInline() {
        return true;
    }

    public boolean isInFlatArray() {
        return klass != null;
    }

    @Override
    public Klass getKlass() {
        return isInFlatArray() ? klass : super.getKlass();
    }

    @Override
    public void iterateFields(OopVisitor visitor, boolean doVMFields) {
        if (isInFlatArray()) {
            ((InlineKlass)getKlass()).iterateNonStaticFields(visitor, this);
        } else {
            super.iterateFields(visitor, doVMFields);
        }
    }

    @Override
    public void printValueOn(PrintStream tty) {
        tty.print("Inlined object");
    }
}
