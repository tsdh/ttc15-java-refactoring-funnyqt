package test4;

public class A {
    int field;
}

class B extends A {
    // hides A.field
    int field;
}
