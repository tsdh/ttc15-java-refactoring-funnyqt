package test3;


public class A {
	int aField = 17;
	String getSentence() {
		return" is the answer!";
	}
	String getFieldCannotBePulled() {
	}
}

class B extends A {
	String bField = aField + "bing";
	String canBePulled1() {
		return aField + getSentence();
	}
	String canBePulled2() {
		return getFieldCannotBePulled();
	}
	String getFieldCannotBePulled() {
		return bField;
	}
	String cannotBePulled() {
		return bField;
	}
}

class C extends A {
	String cField = aField + "bong";
	String canBePulled1() {
		return aField + getSentence();
	}
	String canBePulled2() {
		return getFieldCannotBePulled();
	}
	String getFieldCannotBePulled() {
		return bField;
	}
	String cannotBePulled() {
		return cField;
	}
}



