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
    private static final IFn PARSE_DIRECTORY
	= Clojure.var("ttc15-java-refactoring-funnyqt.jamopp/parse-directory");
    private static final IFn SAVE_JAVA_RESOURCE_SET
	= Clojure.var("ttc15-java-refactoring-funnyqt.jamopp/save-java-rs");
    private static final IFn JAMOPP_TO_PG
	= Clojure.var("ttc15-java-refactoring-funnyqt.jamopp2pg/jamopp2pg");
    private static final IFn PREPARE_PG_TO_JAMOPP_MAP
	= Clojure.var("ttc15-java-refactoring-funnyqt.jamopp2pg/prepare-pg2jamopp-map");
    private static final IFn FIND_TCLASS
	= Clojure.var("ttc15-java-refactoring-funnyqt.refactor/find-tclass");
    private static final IFn FIND_TMETHODSIGNATURE
	= Clojure.var("ttc15-java-refactoring-funnyqt.refactor/find-tmethodsig");
    private static final IFn REF_PULL_UP_METHOD
	= Clojure.var("ttc15-java-refactoring-funnyqt.refactor/pull-up-method");

    private Object jamoppRS;
    private Resource programGraph;
    private Object pgToJamoppMapAtom;
    private ArrayList<IFn> synchronizeFns = new ArrayList<IFn>();

    public String getPluginName() {
	return "FunnyQT";
    }

    public boolean usesProgramGraph() {
	return true;
    }

    public boolean createProgramGraph(String path) {
	Resource programGraph = new ResourceImpl();
	String[] dirs = path.split("/");
	String basePackage = null;
	for (int i = 0; i < dirs.length; i++) {
	    if ("src".equals(dirs[i])) {
		basePackage = dirs[i+1];
		break;
	    }
	}
	if (basePackage == null) {
	    throw new RuntimeException("Can't figure out base-package from " + path);
	}
	Object trace =
	    JAMOPP_TO_PG.invoke(jamoppRS, programGraph, basePackage);
	pgToJamoppMapAtom = PREPARE_PG_TO_JAMOPP_MAP.invoke(trace);
	return true;
    }

    public void setProgramLocation(String path) {
	jamoppRS = PARSE_DIRECTORY.invoke(path);
    }

    public boolean applyPullUpMethod(Pull_Up_Refactoring pur) {
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
	for (IFn synchronizer : synchronizeFns) {
	    synchronizer.invoke();
	}
	synchronizeFns.clear();
	SAVE_JAVA_RESOURCE_SET.invoke(jamoppRS);
	return true;
    }
}
