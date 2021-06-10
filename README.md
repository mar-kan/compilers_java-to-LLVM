Compilers Project 3
-----------------
Kanellaki Maria-Anna  -  1115201400060


-----------------

Compile instructions: $ make

Execution instructions: $ java Main [input files]

-----------------

Package <symbols> with the classes is almost intact from proj2. Offsets were added inside the classes (for methods and 
variables.) Also the register counters are stored in methods to continue from there in overriding methods.

Visitor1 was kept almost intact to fill the <symbols>'s classes . Types are now used in the LLVM format (in symbols too).

package llvmTranslator was created, including the new visitor that is used for the translation and a class that manages
the output file and writes the llvm.

llvmVisitor's methods mostly return the counter register which was used last in the function.
In some methods the classname is return along with the register in the format <reg>,<classname>.



-----------------

