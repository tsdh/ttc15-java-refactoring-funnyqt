package ttc15_java_refactoring_funnyqt;

import java.io.File;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import clojure.java.api.Clojure;
import clojure.lang.IFn;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.common.util.EList;

import ttc.testsuite.interfaces.TestInterface;
import ttc.testdsl.tTCTest.*;

public class TestInterfaceImpl implements TestInterface {

    static {
	System.out.println("Initializing JavaPackage and setting context classloader");
	org.emftext.language.java.JavaPackage jp = org.emftext.language.java.JavaPackage.eINSTANCE;
	Thread.currentThread().setContextClassLoader(TestInterfaceImpl.class.getClassLoader());
    }

    private static final IFn R = Clojure.var("clojure.core", "require");
    private static String NS_JAMOPP = "ttc15-java-refactoring-funnyqt.jamopp";
    private static String NS_JAMOPP2PG = "ttc15-java-refactoring-funnyqt.jamopp2pg";
    private static String NS_REFACTOR = "ttc15-java-refactoring-funnyqt.refactor";

    static {
	System.out.println("Loading FunnyQT solution namespaces");
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
    private static final IFn PULL_UP_MEMBER = Clojure.var(NS_REFACTOR, "pull-up-member");
    private static final IFn CREATE_SUPERCLASS = Clojure.var(NS_REFACTOR, "create-superclass");

    private Object jamoppRS;
    private Resource programGraph;
    private Object pgToJamoppMapAtom;
    private ArrayList<IFn> synchronizeFns = new ArrayList<IFn>();
    private String programPath;
    private String testName;

    public String getPluginName() {
	System.out.println("getPluginName()");
	return "FunnyQT";
    }

    public boolean usesProgramGraph() {
	System.out.println("usesProgramGraph()");
	return true;
    }

    public void copyDir(String from, String baseDir, String subDir) {
	try {
	    File bd = new File(baseDir);
	    if (!bd.exists()) bd.mkdir();
	    File copyFolder = new File(baseDir, subDir);
	    if (copyFolder.exists() && copyFolder.isDirectory()) {
		System.out.println("Deleting folder " + copyFolder.getPath());
		copyFolder.delete();
	    }
	    copyFolder.mkdir();
	    System.out.println("Copying " + from + " to " + copyFolder.getPath());
	    Process p = Runtime.getRuntime().exec("cp -R " + from + " " + copyFolder.getPath());
	    p.waitFor();
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }
    public boolean createProgramGraph(String path) {
	programPath = path;
	System.out.println("createProgramGraph(" + path + ")");
	jamoppRS = PARSE_DIRECTORY.invoke(path);
	programGraph = new ResourceImpl();
	Object trace = JAMOPP_TO_PG.invoke(jamoppRS, programGraph);
	pgToJamoppMapAtom = PREPARE_PG_TO_JAMOPP_MAP.invoke(trace);
	return true;
    }

    public void setProgramLocation(String path) {
	System.out.println("setProgramLocation(" + path + ")");
	jamoppRS = PARSE_DIRECTORY.invoke(path);
    }

    public boolean applyPullUpMethod(Pull_Up_Refactoring pur) {
	testName = pur.getName();
	String tClassQN = pur.getParent().getPackage() + "." + pur.getParent().getClass_name();
	String tMethodName = pur.getMethod().getMethod_name();
	EList<Java_Class> javaClasses = pur.getMethod().getParams();
	List<String> tMethodParamQNs = new ArrayList<String>();
	for (Java_Class c : javaClasses) {
	    String pkg = c.getPackage();
	    if (pkg != null)
		tMethodParamQNs.add( pkg + "." + c.getClass_name());
	    else
		tMethodParamQNs.add(c.getClass_name());
	}
	System.out.println("applyPullUpMethod(" + tClassQN + ", " + tMethodName + ")");
	Object tClass = FIND_TCLASS.invoke(programGraph, tClassQN);
	if (tClass == null) {
	    System.out.println("No such TClass " + tClassQN);
	    return false;
	}
	Object tMethodSig = FIND_TMETHODSIGNATURE.invoke(programGraph, tMethodName, tMethodParamQNs);
	if (tMethodSig == null) {
	    System.out.print("No such TMethodSignature " + tMethodName + "(");
	    boolean first = true;
	    for (String p :tMethodParamQNs) {
		if (first)
		    first = false;
		else
		    System.out.print(", ");
		System.out.print(p);
	    }
	    System.out.println(")");
	    return false;
	}
	IFn s = (IFn) PULL_UP_MEMBER.invoke(programGraph, pgToJamoppMapAtom, tClass, tMethodSig);
	if (s == null) {
	    System.out.println("The pull-up-method rule didn't match!");
	    return false;
	} else {
	    System.out.println("The pull-up-method rule did match!");
	    synchronizeFns.add(s);
	    return true;
	}
    }

    public boolean applyCreateSuperclass(Create_Superclass_Refactoring csr) {
	testName = csr.getName();
	List<Object> classes = new ArrayList<Object>();
	System.out.print("applyCreateSuperClass([");
	boolean first = true;
	for (Java_Class c :csr.getChild().getClasses()) {
	    classes.add(FIND_TCLASS.invoke(programGraph, c.getPackage()
					   + "." + c.getClass_name()));
	    if (first)
		first = false;
	    else
		System.out.print(", ");
	    System.out.print(c.getPackage() + "." + c.getClass_name());
	}
	System.out.println("], " + csr.getTarget().getPackage() + "."
			   + csr.getTarget().getClass_name() + ")");
	IFn s = (IFn) CREATE_SUPERCLASS.invoke(programGraph, pgToJamoppMapAtom, classes,
					       csr.getTarget().getPackage() + "."
					       + csr.getTarget().getClass_name());
	if (s == null) {
	    System.out.println("The create-superclass rule didn't match!");
	    return false;
	} else {
	    System.out.println("The create-superclass rule did match!");
	    synchronizeFns.add(s);
	    return true;
	}
    }

    public void setPermanentStoragePath(File path) {}

    public void setLogPath(File path) {}

    public void setTmpPath(File path) {}

    public boolean synchronizeChanges() {
	try {
	    //copyDir(programPath, "/home/horn/tmp/JR/", testName + "-before/");
	    System.out.println("synchronizeChanges(): "
			       + synchronizeFns.size() + " changes to be applied");
	    for (IFn synchronizer : synchronizeFns) {
		synchronizer.invoke(jamoppRS);
	    }
	    SAVE_JAVA_RESOURCE_SET.invoke(jamoppRS);
	    //Thread.sleep(1000000);
	    //copyDir(programPath, "/home/horn/tmp/JR/", testName + "-after/");
	    return true;
	} catch (Exception e) {
	    e.printStackTrace();
	    return false;
	} finally {
	    synchronizeFns.clear();
	}
    }
}
