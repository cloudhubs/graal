# Capstone Introduction

## GraalVM - terminology (simplified)
* Graal - An advanced optimizing just-in-time (JIT) compiler for Java 
* GraalVM - A JVM distribution with GraalVM as the (tier 2) JIT compiler
* Native Image - An ahead-of-time (AOT) compiler for Java.
  * [Documentation](https://www.graalvm.org/22.0/reference-manual/native-image/)
  * [Related paper](https://dl.acm.org/doi/10.1145/3360610).
* Graal IR - An intermediate representation used in Graal. It is graph based and in SSA form.
  * More details can be found [here](https://ssw.jku.at/General/Staff/GD/APPLC-2013-paper_12.pdf).
* Truffle - A framework for the implementation of programming language runtimes as AST interpreters.
  * AST interpreters are generally very slow compared to bytecode interpreters, JIT compilers or AOT compilers, but Truffle uses partial evaluation (futamura projections) to JIT-compile the ASTs of hot functions.
  * [Documentation](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/)
  * [Related paper](http://lafo.ssw.uni-linz.ac.at/papers/2013_Onward_OneVMToRuleThemAll.pdf)

## Useful links
* Graal-prophet git repository
  * https://github.com/d-kozak/graal
  * branch prophet-plugin-master 
    * should be the default
* [Developing native image](https://github.com/d-kozak/graal/blob/prophet-plugin-master/docs/reference-manual/native-image/contribute/DevelopingNativeImage.md)
* [mx](https://github.com/graalvm/mx)
  * The build tool used to build GraalVM projects.
* Ideal Graph Visualizer (IGV)
  * Used to visualize Graal IR graphs.
  * [More info](https://docs.oracle.com/en/graalvm/enterprise/20/docs/tools/igv/)

## Initial tasks
* Setup a working environment
  * clone [mx](https://github.com/graalvm/mx)
  * add it to PATH
  * clone graal-prophet https://github.com/d-kozak/graal
  * open the project in an IDE using mx intelijinit
  * build native image of helloworld
  * view the graph of the main method in IGV
* Look at the source code of the [prophet plugin](https://github.com/d-kozak/graal/tree/prophet-plugin-master/substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/prophet)

## Extra
* Design a more general approach for the annotation extraction.
  * Try to think of an approach that would generalize to other Spring Entities (and then, as a next step, to other java frameworks) without too much extra code.
  * Do not depend on concrete annotations in the code.