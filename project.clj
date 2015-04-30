(defproject ttc15-java-refactoring-funnyqt "0.1.0-SNAPSHOT"
  :description "The FunnyQT solution to the TTC 2015 Java Refactoring case."
  :url "http://example.com/FIXME"
  :license {:name "GNU General Public License, Version 3 (or later)"
            :url "http://www.gnu.org/licenses/gpl.html"
            :distribution :repo}
  :dependencies [[funnyqt "0.47.3"]
                 [org.emftext.language/org.emftext.language.java "1.4.1-SNAPSHOT"
                  :exclusions [org.eclipse.emf/org.eclipse.emf.ecore
                               org.eclipse.emf/org.eclipse.emf.ecore.change]]
                 [org.emftext.language/org.emftext.language.java.resource "1.4.1-SNAPSHOT"
                  :exclusions [org.eclipse.emf/org.eclipse.emf.ecore
                               org.eclipse.emf/org.eclipse.emf.ecore.change]]
                 [org.emftext.language/org.emftext.language.java.resource.java "1.4.1-SNAPSHOT"
                  :exclusions [org.eclipse.emf/org.eclipse.emf.ecore
                               org.eclipse.emf/org.eclipse.emf.ecore.change]]]
  :global-vars {*warn-on-reflection* true}
  :java-source-paths ["java-src"]
  :resource-paths ["resources" "resources/TTCTestInterface.jar"]
  :jvm-opts ^:replace ["-Xmx1G"]
  :repositories [["JaMoPP Snapshots" "http://jamopp.org/maven-repository-snapshot/"]
                 ["EMFText Snapshots" "http://emftext.org/maven-repository-snapshot/"]])
