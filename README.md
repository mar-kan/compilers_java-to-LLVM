Compilers Project 2
-----------------
Kanellaki Maria-Anna  -  1115201400060


-----------------

Compile instructions: $ make

Execution instructions: $ java Main [input files]

-----------------

Package <symbols> with the classes is almost intact from proj2. Offsets were added as fields for methods and variables.

Visitor1 was kept almost intact to fill the classes of <symbols>. Types are now inserted with the LLVM format and IDs
with a '%' in front.

Translator is the new Visitor that was created to translate the minijava inputs.

OutputFile was also created in <myVisitors> package for convenience. It is used to print everything in files.



-----------------

