package test2;

public class A {
    int foo(double d) {
	return 0;
    }
}

class B extends A {
    static int STATIC_FIELD = 42;

    int foo(double d) {
	return 1;
    }

    static int staticMethod() {
	return STATIC_FIELD;
    }
}
