# The FunnyQT Solution to the TTC 2015 Java Refactoring Case

This project contains the [FunnyQT](http://funnyqt.org) solution to the
[TTC](http://www.transformation-tool-contest.eu/)
[Java Refactoring Case](https://github.com/Echtzeitsysteme/java-refactoring-ttc).

## Usage

First, you need to get the `lein` script from the
[Leiningen homepage](http://leiningen.org/) and put in on your `PATH`.

### Running the ARTE tests

Simply run `lein uberjar` in the project directory.  Leiningen will
automatically fetch all required dependencies such as Clojure and FunnyQT and
install them into your local maven repository, and then build and package the
project.

That will create a file
`target/ttc15-java-refactoring-funnyqt-0.1.0-SNAPSHOT-standalone.jar`
containing the solution with all dependencies.  This is the JAR you should load
as solution in ARTE.

### Interactive refactoring

To let the solution propose refactorings (Extension 2), just run `lein test` on
the command line.  This will propose refactorings for the java classes in
`test-src/`.

## License

Copyright © 2015 Tassilo Horn <horn@uni-koblenz.de>

Distributed under the
[GNU General Public License, version 3](https://www.gnu.org/copyleft/gpl.html),
or at your opinion any later version.
