package test3;

public class A {
    protected int aField = 17;

    String getSentence() {
	return " is the answer!";
    }
}

class B extends A {
    int bField = aField + 1;

    String getString() {
	return aField + getSentence();
    }
}

class C extends A {
    int cField = aField - 1;

    String getString() {
	return aField + getSentence();
    }
}
