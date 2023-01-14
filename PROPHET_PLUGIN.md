# Graal-Prophet Integration

## My progress
* Getting to know various cloudhubs projects
* Prophet-plugin for Native Image
* Entity extraction
    * code & demo
* Very minor changes in prophet-utils
* Bounded context
    * code & demo
* Parsing Graal IR graphs and traversing them
* Callgraph extraction
    * code & mini-demo
* Conclusions

## Questions
* Where can I find the expected results for comparison?
    * Should I generate them via the current infrastructure?
* What tools do you use to compare the analysis results?
    * What data format do you use for the comparison?
    * Do you have your own json-semantic-diff tool or do you use some out-of-the-box solution?
* What are the rules for contribution to the cloudhubs repositories?
    * Is there any CI pipeline?
* Where should I keep the prophet-plugin?
    * It is currently in our internal git repository, which is not ideal.
    * Should we fork graalvm-repository and keep the prophet specific version in the cloudhubs organisation?

## Issues when using GraalVM Native Image

### 1) Overhead
It is necessary to start Native Image as a separate process for each analyzed microservice. We could squash all the jar files together, but then there could be collisions with classnames.

### 2) Splitting the analysis logic into _NI-specific_ and _Prophet-specific_ parts
Since NI is started as a separate per-module process, it needs its own domain representation that can be serialized and transferred back to Prophet.
Currently, the split is done on the __Module__ level. That requires duplicating the definitions of the Module class
and all related classes (Entity, Field, Annotation, Name) into Native Image.

Alternatively, it could be solved by making NI depend on the __prophet-dto__ project.
However, I am not sure whether such dependency is a good idea and it is also not so easy to introduce for technical reasons (custom build system mx, custom repository jitpack).

### 3) Compiling the analyzed app
If the input is already compiled, it is not a problem. But should the input be any git repository, then it can cause
issues, because:

- The structure of the repository can be arbitrary. How to locate the root folders for each microservice?
- Specific support for each build system is needed.
- The jar files containing the definitions for relevant annotations have to be on the classpath.
    - Otherwise, they [cannot](https://www.baeldung.com/classnotfoundexception-missing-annotation) be accessed reflectively.

However, these problems can be mitigated by assuming/enforcing a specific structure,
as was done in this demo.

Kubernetes?

### 4) Flat classpath
Native Image does not allow nested hierarchy of classloaders. All jar files have to be on the classpath directly and the class files are only loaded from specific locations. Single
fat-jar with a custom classloader (spring boot strategy) therefore does not work by default. It is necessary to unpack the fat-jar and configure the classpath accordingly: `-cp classes:libs/*`.

### 5) No Spring specific extensions by default (cannot fold @Value nodes)
We could either try to utilize Spring Native Support or write our own solution.

## TODO list

- [ ] entities
    - [x] extract entities - only classnames
    - [x] extract fields and mappings
    - [x] call ni-prophet from prophet
    - [x] load the ni-prophet output as a module in prophet
    - [ ] compare the system context from original approach with ours
        - [ ] fix any discrepancies
    - [ ] use the system context to compute bounded context
- [ ] rest flow
    - [x] get familiar with the data structure for representing rest calls
    - [x] parse relevant application methods
    - [x] lookup for rest calls
    - [ ] extract the same information from graal IR
- [x] prepare a github fork to share with students

## Next
- [ ] space on github
  - [ ] trainticket results to compare
  - [ ] analyze
  - [ ] check differences
  - [ ] analyze 6 client projects
  - [ ] extension points to analyze 'real application'
  - [ ] evolution
  - [ ] tomas tomasek
  - [ ] service view on train tickets
  - [ ] restControllers
  - [ ] prepare a few examples of truffle ASTs
  - [ ] python microservices
  - [ ] adam hrbac - ask his support
