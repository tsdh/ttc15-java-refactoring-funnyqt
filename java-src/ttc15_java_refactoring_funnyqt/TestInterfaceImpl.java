package ttc15_java_refactoring_funnyqt;

import java.io.File;
import java.util.Map;
import java.util.ArrayList;

import clojure.java.api.Clojure;
import clojure.lang.IFn;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;

import ttc.testsuite.interfaces.TestInterface;
import ttc.testdsl.tTCTest.*;

public class TestInterfaceImpl implements TestInterface {

    static {
	Thread.currentThread().setContextClassLoader(TestInterfaceImpl.class.getClassLoader());
    }

    private static final IFn R = Clojure.var("clojure.core", "require");
    private static String NS_JAMOPP = "ttc15-java-refactoring-funnyqt.jamopp";
    private static String NS_JAMOPP2PG = "ttc15-java-refactoring-funnyqt.jamopp2pg";
    private static String NS_REFACTOR = "ttc15-java-refactoring-funnyqt.refactor";
    // Require the namespaces
    static {
	for (String ns : new String[] {NS_JAMOPP, NS_JAMOPP2PG, NS_REFACTOR}) {
	    R.invoke(Clojure.read(ns));
	}
    }
    private static final IFn PARSE_DIRECTORY = Clojure.var(NS_JAMOPP, "parse-directory");
    private static final IFn SAVE_JAVA_RESOURCE_SET = Clojure.var(NS_JAMOPP, "save-java-rs");
    private static final IFn JAMOPP_TO_PG = Clojure.var(NS_JAMOPP2PG, "jamopp2pg");
    private static final IFn PREPARE_PG_TO_JAMOPP_MAP = Clojure.var(NS_JAMOPP2PG, "prepare-pg2jamopp-map");
    private static final IFn FIND_TCLASS = Clojure.var(NS_REFACTOR, "find-tclass");
    private static final IFn FIND_TMETHODSIGNATURE = Clojure.var(NS_REFACTOR, "find-tmethodsig");
    private static final IFn REF_PULL_UP_METHOD = Clojure.var(NS_REFACTOR, "pull-up-method");

    private Object jamoppRS;
    private Resource programGraph;
    private Object pgToJamoppMapAtom;
    private ArrayList<IFn> synchronizeFns = new ArrayList<IFn>();

    public String getPluginName() {
	System.out.println("getPluginName()");
	return "FunnyQT";
    }

    public boolean usesProgramGraph() {
	System.out.println("usesProgramGraph()");
	return true;
    }

    public boolean createProgramGraph(String path) {
	System.out.println("createProgramGraph(" + path + ")");
	if (jamoppRS == null) {
	    jamoppRS = PARSE_DIRECTORY.invoke(path);
	}
	programGraph = new ResourceImpl();
	File dir = new File(path + File.separator + "src");
	String basePackage = dir.listFiles()[0].getName();
	System.out.println("Base-package is " + basePackage);
	Object trace =
	    JAMOPP_TO_PG.invoke(jamoppRS, programGraph, basePackage);
	pgToJamoppMapAtom = PREPARE_PG_TO_JAMOPP_MAP.invoke(trace);
	return true;
    }

    public void setProgramLocation(String path) {
	System.out.println("setProgramLocation(" + path + ")");
	jamoppRS = PARSE_DIRECTORY.invoke(path);
    }

    public boolean applyPullUpMethod(Pull_Up_Refactoring pur) {
	System.out.println("applyPullUpMethod(" + pur + ")");
	IFn s = (IFn) REF_PULL_UP_METHOD
	    .invoke(programGraph,
		    pgToJamoppMapAtom,
		    FIND_TCLASS.invoke(programGraph, pur.getParent()),
		    FIND_TMETHODSIGNATURE.invoke(programGraph, pur.getMethod()));
	if (s == null) {
	    return false;
	} else {
	    synchronizeFns.add(s);
	    return true;
	}
    }

    public boolean applyCreateSuperclass(Create_Superclass_Refactoring csr) {
	// TODO: Implement me!
	return false;
    }

    public void setPermanentStoragePath(File path) {}

    public void setLogPath(File path) {}

    public void setTmpPath(File path) {}

    public boolean synchronizeChanges() {
	System.out.println("synchronizeChanges()");
	for (IFn synchronizer : synchronizeFns) {
	    synchronizer.invoke();
	}
	synchronizeFns.clear();
	SAVE_JAVA_RESOURCE_SET.invoke(jamoppRS);
	return true;
    }
}
