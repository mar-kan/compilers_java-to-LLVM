package llvmTranslator;

import symbols.AllClasses;
import symbols.ClassData;
import symbols.MethodData;
import symbols.VariableData;

import java.io.*;

/** class manages the output .ll file **/
public class LlvmOutput {

    private File file;
    private AllClasses allClasses;
    private Utils utils = new Utils();

    private int reg_num=0;
    private int if_num=0;
    private int while_num=0;
    private int oob_num=0;
    private int nsz_num=0;
    private int and_num=0;


    public LlvmOutput(String filename, AllClasses allClasses) throws IOException
    {
        this.file = new File(filename);
        this.allClasses = allClasses;

        this.writeVtables(allClasses);
        this.writeBoilerplate();
    }

    /** writes a string **/
    public void writeString(String str) throws IOException
    {
        FileWriter writer = new FileWriter(file.getName(), true);   // appends to file

        writer.write(str);
        writer.flush();
        writer.close();
    }

    /** writes boilerplate **/
    public void writeBoilerplate() throws IOException
    {
        this.writeString("declare i8* @calloc(i32, i32)\n" +
                "declare i32 @printf(i8*, ...)\n" +
                "declare void @exit(i32)\n" +
                "\n" +
                "@_cint = constant [4 x i8] c\"%d\\0a\\00\"\n" +
                "@_cOOB = constant [15 x i8] c\"Out of bounds\\0a\\00\"\n" +
                "@_cNSZ = constant [15 x i8] c\"Negative size\\0a\\00\"\n" +
                "\n" +
                "define void @print_int(i32 %i) {\n" +
                "    %_str = bitcast [4 x i8]* @_cint to i8*\n" +
                "    call i32 (i8*, ...) @printf(i8* %_str, i32 %i)\n" +
                "    ret void\n" +
                "}\n" +
                "\n" +
                "define void @throw_oob() {\n" +
                "    %_str = bitcast [15 x i8]* @_cOOB to i8*\n" +
                "    call i32 (i8*, ...) @printf(i8* %_str)\n" +
                "    call void @exit(i32 1)\n" +
                "    ret void\n" +
                "}\n" +
                "\n" +
                "define void @throw_nsz() {\n" +
                "    %_str = bitcast [15 x i8]* @_cNSZ to i8*\n" +
                "    call i32 (i8*, ...) @printf(i8* %_str)\n" +
                "    call void @exit(i32 1)\n" +
                "    ret void\n" +
                "}\n\n");
    }

    /** writes all Vtables **/
    public void writeVtables(AllClasses allClasses) throws IOException
    {
        FileWriter writer = new FileWriter(file.getName());     // overwrites file

        /** printing Vtables for all the classes of the file **/
        for (int i=allClasses.getClasses().size()-1; i>=0; i--) // printing classes in reverse
        {
            ClassData aClass = allClasses.getClasses().get(i);
            int method_count = aClass.getMethodSizeWithExtending();

            writer.write("@."+aClass.getName()+"_vtable = global ["+method_count+" x i8*] ["); // writes class vtable
            writer.flush();
            writer.close();
            boolean wrote = writeClassMethodsInVtable(aClass, false, false);                  // writes all its methods

            // writes every method of the extending that isn't overridden by aClass
            if (aClass.getExtending() != null)
                writeClassMethodsInVtable(aClass.getExtending(), true, wrote);

            writer = new FileWriter(file.getName(), true);   // appends to file
            writer.write("\n]\n\n");
        }

        /** printing main's Vtable **/
        writer.write("@."+allClasses.getMain_class_name()+"_vtable = global ["+allClasses.getMainClass().getMethods().size()+" x i8*] []\n\n");
        writer.flush();
        writer.close();

        /** printing main's methods **/
        writeClassMethodsInVtable(allClasses.getMainClass(), false, false);
    }

    /** writes all methods of a class' Vtable **/
    public boolean writeClassMethodsInVtable(ClassData aClass, boolean isInherited, boolean wrote) throws IOException
    {
        FileWriter writer = new FileWriter(file.getName(), true);   // appends to file
        for (MethodData method : aClass.getMethods())
        {
            if (isInherited && method.isOverridden())   // skips methods that are overwritten by the subclass
                continue;

            if (wrote)
                writer.write(",\n");
            else
                writer.write('\n');

            String type = method.getType();
            if (!type.equals("i32") && !type.equals("i32*") && !type.equals("i1"))
                type = "i8*";

            writer.write("\ti8* bitcast ("+type+" (i8*");
            wrote = true;

            if (method.getArguments() != null)
            {
                for (VariableData argument : method.getArguments())
                {
                    type = argument.getType();
                    if (!type.equals("i32") && !type.equals("i32*") && !type.equals("i1"))
                        type = "i8*";
                    writer.write(", "+type);
                }
            }
            writer.write(")* @"+aClass.getName()+"."+method.getName()+" to i8*)");
        }
        writer.flush();
        writer.close();

        return wrote;   // returns if anything was written in the file
    }

    /** writes a method declaration **/
    public void writeMethodDeclaration(String classname, String methodName, String methodType, String args) throws IOException
    {
        // finds method and gets its register count
        MethodData myMethod = allClasses.searchClass(classname).searchMethod(methodName);
        reg_num = myMethod.getMyRegCount();

        // writing method declaration
        if (args == null)
            this.writeString("\ndefine "+methodType+" @"+classname+"."+methodName+"(i8* %this) {\n");
        else
        {
            this.writeString("\ndefine "+methodType+" @"+classname+"."+methodName+"(i8* %this, "+args+") {\n");
            String[] split_args = args.split(" ");
            for (int i=1; i<split_args.length; i+=2)
            {
                if (split_args[i].contains(","))    // removes commas
                    split_args[i] = split_args[i].substring(0, split_args[i].indexOf(","));

                // allocating and storing every argument in variable
                this.writeString("\t%"+split_args[i].substring(split_args[i].indexOf(".")+1)+" = alloca "+split_args[i-1]+'\n'+
                        "\tstore "+split_args[i-1]+" "+split_args[i]+", "+split_args[i-1]+"* %"+split_args[i].substring(split_args[i].indexOf(".")+1)+"\n\n");
            }
        }

        // sets method's reg_count
        myMethod.setMyRegCount(reg_num);  // sets it to the 1st unused reg_num
    }

    /** writes assignment expressions **/
    public void writeAssignment(String id, String expr, String scope) throws IOException
    {
        // finding variable of id
        VariableData var = allClasses.findVariable(id, scope);
        assert var != null;

        /** expr is a variable **/
        VariableData variable = allClasses.findVariable("%"+expr, scope);
        if (variable != null)
        {
            // loading expr
            if (var.getType().equals("i32") || var.getType().contains("i32") || var.getType().equals("i1"))
                this.writeString("\t%_"+reg_num+++" = load "+variable.getType()+", "+variable.getType()+"* %"+expr+'\n');
            else
                this.writeString("\t%_"+reg_num+++" = load i8*, i8** %"+expr+'\n');

            expr = "%_"+(reg_num-1);
        }

        /** var is a field **/
        if (allClasses.varIsField(var.getName(), scope))
            id = this.writeLoadField(var);

        // assignment
        if (var.getType().equals("i32") || var.getType().contains("i32") || var.getType().equals("i1"))
            this.writeString("\tstore "+var.getType()+" "+expr+", "+var.getType()+"* "+id+'\n');
        else
            this.writeString("\tstore i8* "+expr+", i8** "+id+'\n');
    }

    /** writes array assignment **/
    public void writeArrayAssignment(String arrayName, String index, String expr, String scope) throws IOException
    {
        String array_address = "%_"+reg_num++;  // stores the address of the array
        this.writeString("\t"+array_address+" = load i32*, i32** "+arrayName+"\n"+
                "\t%_"+(reg_num++)+" = load i32, i32* %_"+(reg_num-2)+"\n");

        this.writeOobCheck(index);

        // index takes its literal value if it is a variable
        if (!utils.isIntegerLiteral(index))
        {
            VariableData var = allClasses.findVariable(index, scope);
            assert var != null;

            index = String.valueOf(var.getValue());
        }

        // finds index and stores expr in id[index]
        this.writeString("\t%_"+(reg_num++)+" = add i32 1, "+index+"\n"+
                "\t%_"+(reg_num++)+" = getelementptr i32, i32* "+array_address+", i32 %_"+(reg_num-2)+'\n'+
                "\tstore i32 "+(Integer.parseInt(index)+1)+", i32* %_"+(reg_num-1)+"\n\n");
    }

    /** writes the check for oob errors of index **/
    public void writeOobCheck(String index) throws IOException
    {
        // checks index for errors
        this.writeString("\t%_"+(reg_num++)+" = icmp sge i32 "+index+", 0\n"+
                "\t%_"+(reg_num++)+" = icmp slt i32 "+index+", %_"+(reg_num-3)+'\n'+
                "\t%_"+(reg_num++)+" = and i1 %_"+(reg_num-3)+", %_"+(reg_num-2)+'\n'+
                "\tbr i1 %_"+(reg_num-1)+", label %oob_ok_"+oob_num+", label %oob_err_"+oob_num+"\n\n"+
                "\toob_err_"+oob_num+":\n\tcall void @throw_oob()\n"+
                "\tbr label %oob_ok_"+oob_num+"\n\n\toob_ok_"+(oob_num++)+":\n");
    }

    /** writes an if statement **/
    public void writeIfStart(String expr) throws IOException
    {
        this.writeString("\tbr i1 "+expr+", label %if_then_"+if_num+", label %if_else_"+if_num+"\n\n");
    }

    /** writes then part of the if **/
    public void writeIfThenStart() throws IOException
    {
        this.writeString("\tif_then_"+if_num+":\n");
    }

    /** writes else part of the if **/
    public void writeIfElseStart() throws IOException
    {
        this.writeString("\tif_else_"+if_num+":\n");
    }

    /** writes closing of the if **/
    public void goToIfEnd() throws IOException
    {
        this.writeString("\tbr label %if_end_"+if_num+"\n\n");
    }

    public void writeIfEnd() throws IOException
    {
        this.writeString("\tif_end_"+(if_num++)+":\n");
    }

    /** writes print statement **/
    public void writePrintStatement(String expr) throws IOException
    {
        if (!utils.isIntegerLiteral(expr) && !expr.contains("%_"))
        {
            this.writeString("\t%_"+reg_num+" = load i32, i32* %"+expr+"\n"); // loads variable
            expr = "%_"+reg_num++;
        }

        this.writeString("\tcall void (i32) @print_int(i32 "+expr+")\n");
    }

    /** writes compare expressions **/
    public String writeCompareExpr(String expr1, String expr2) throws IOException
    {
        if (!utils.isIntegerLiteral(expr1))
        {
            expr1 = "%"+expr1;
            this.writeString("\t%_"+reg_num+" = load i32, i32* "+expr1+'\n');
            expr1 = "%_"+reg_num++;
        }

        if (!utils.isIntegerLiteral(expr2))
        {
            expr2 = "%"+expr2;
            this.writeString("\t%_"+(reg_num++)+" = load i32, i32* "+expr2+'\n');
            expr2 = "%_"+reg_num++;
        }
        String expr_reg = String.valueOf(reg_num++);
        this.writeString("\t%_"+expr_reg+" = icmp slt i32 "+expr1+", "+expr2+'\n');

        return "%_"+expr_reg;
    }

    /** writes arithmetical operations **/
    public String writeArithmeticOperation(String expr1, String expr2, int op, String scope) throws Exception
    {
        VariableData var1 = allClasses.findVariable(expr1, scope);
        VariableData var2 = allClasses.findVariable(expr2, scope);

        if (var1 != null)
        {
            this.writeString("\t%_"+reg_num+" = load i32, i32* "+var1.getName()+"\n");
            expr1 = "%_"+reg_num++;
        }
        ;
        if (var2 != null)
        {
            this.writeString("\t%_"+reg_num+" = load i32, i32* "+var2.getName()+"\n");
            expr2 = "%_"+reg_num++;
        }

        if (op=='+')
            this.writeString("\t%_"+reg_num+" = add i32 "+expr1+", "+expr2+"\n");
        else if (op=='-')
            this.writeString("\t%_"+reg_num+" = sub i32 "+expr1+", "+expr2+"\n");
        else if (op=='*')
            this.writeString("\t%_"+reg_num+" = mul i32 "+expr1+", "+expr2+"\n");
        else if (op=='&')
            this.writeString("\t%_"+reg_num+" = and i32 "+expr1+", "+expr2+"\n");
        else
            throw new Exception("Wrong op");

        return "%_"+reg_num++;    // returns the register of the result
    }

    /** writes array lookup **/
    public String writeArrayLookup(String arrayName, String index) throws IOException
    {
        String array_reg = "%_"+reg_num++;

        this.writeString("\t"+array_reg+" = load i32*, i32** %"+arrayName+"\n" +
                "\t%_"+reg_num+++" = load i32, i32* %_"+(reg_num-2)+"\n");

        this.writeOobCheck(index);

        this.writeString("\t%_"+reg_num+++" = add i32 1, "+index+"\n" +
                "\t%_"+reg_num+++" = getelementptr i32, i32* "+array_reg+", i32 %_"+(reg_num-2)+"\n" +
                "\t%_"+reg_num+" = load i32, i32* %_"+(reg_num-1)+"\n\n");

        return "%_"+reg_num++;
    }

    /** writes method calls **/
    public String writeMessageSend(String objectPtr, MethodData method, String args) throws IOException
    {
        this.writeString("\t%_"+reg_num+++" = bitcast i8* "+objectPtr+" to i8***\n"+
                "\t%_"+reg_num+++" = load i8**, i8*** %_"+(reg_num-2)+'\n'+
                "\t%_"+reg_num+++" = getelementptr i8*, i8** %_"+(reg_num-2)+", i32 "+method.getOffset()+"\n"+
                "\t%_"+reg_num+++" = load i8*, i8** %_"+(reg_num-2)+'\n');

        this.writeString("\t%_"+reg_num+++" = bitcast i8* %_"+(reg_num-2)+" to "+method.getType()+" (i8*");

        if (args.equals(""))
            this.writeString(")*\n\t%_"+reg_num+++" = call "+method.getType()+" %_"+(reg_num-2)+"(i8* "+objectPtr+")\n");
        else
        {
            String[] split_args = args.split(" ");
            StringBuilder decl_args = new StringBuilder();
            StringBuilder call_args = new StringBuilder();
            for (int i=0; i<split_args.length; i++)
            {
                if (split_args[i].contains(","))    // removes commas
                    split_args[i] = split_args[i].substring(0, split_args[i].indexOf(","));

                if (!call_args.toString().equals(""))
                    call_args.append(", ");
                call_args.append(method.getArguments().get(i).getType()).append(" ").append(split_args[i]);

                if (!decl_args.toString().equals(""))
                    decl_args.append(",");
                decl_args.append(method.getArguments().get(i).getType());
            }
            this.writeString(","+decl_args+")*\n\t%_"+reg_num+++" = call "+method.getType()+" %_"+(reg_num-2)+"(i8* "+
                    objectPtr+", "+call_args+")\n");
        }
        return "%_"+(reg_num-1);    // returns call receiver register
    }

    /** writes array allocations **/
    public String writeArrayAlloc(String size) throws IOException
    {
        this.writeString("\t%_"+(reg_num++)+" = add i32 1, "+ size+'\n');

        // check that size is >= 1
        this.writeString("\t%_"+reg_num+++" = icmp sge i32 %_"+(reg_num-2)+", 1\n\tbr i1 %_"+(reg_num-1)+
                ", label %nsz_ok_"+nsz_num+", label %nsz_err_"+nsz_num+"\n\n\tnsz_err_"+nsz_num+":\n"+
                "\tcall void @throw_nsz()\n\tbr label %nsz_ok_"+if_num+"\n\n\tnsz_ok_"+(nsz_num++)+":\n");

        // allocation
        this.writeString("\t%_"+(reg_num++)+" = call i8* @calloc(i32 %_"+(reg_num-3)+", i32 4)\n"+
                "\t%_"+(reg_num++)+" = bitcast i8* %_"+(reg_num-2)+" to i32*\n\t" +
                "store i32 2, i32* %_"+(reg_num-1)+"\n\n");

        return "%_"+(reg_num - 1);  // returns register of ptr to array
    }

    /** writes object allocations **/
    public String writeObjectAlloc(ClassData aClass) throws IOException
    {
        int bytes = 8 /* v-table */ + aClass.getField_offsets() /* offsets including last field's*/;
        int object_reg = reg_num++;
        this.writeString("\t%_"+object_reg+" = call i8* @calloc(i32 1, i32 "+bytes+")\n");

        int method_count = aClass.getMethodSizeWithExtending();

        String vtablePtr = "%_"+reg_num++;
        this.writeString("\t"+vtablePtr+" = bitcast i8* %_"+(reg_num-2)+" to i8***\n"+
                "\t%_"+reg_num+" = getelementptr ["+method_count+" x i8*], ["+method_count+" x i8*]* @."+aClass.getName()+"_vtable, i32 0, i32 0\n"+
                "\tstore i8** %_"+(reg_num++)+", i8*** "+vtablePtr+"\n");

        return "%_"+object_reg; // returns register of ptr to object
    }


    /** writes the instructions required to load a field in a register before using it **/
    public String writeLoadField(VariableData var) throws IOException
    {
        String type;
        if (var.getType().equals("i1") || var.getType().equals("i32") || var.getType().contains("i32"))
            type = var.getType();   // keeps field type
        else
            type = "i8*";   // converts field(object) type to i8*

        this.writeString("\t%_"+reg_num+++" = getelementptr i8, i8* %this, "+type+" "+(var.getOffset()+8)+"\n"
                +"\t%_"+reg_num+++" = bitcast i8* %_"+(reg_num-2)+" to "+type+"*\n");

        return "%_"+(reg_num-1);  // returns the num of register of ptr to this->var
    }

    /** writes the load of a variable's value **/
    public String writeLoadValue(VariableData var, String load_name) throws IOException
    {
        String type;
        if (var.getType().equals("i1") || var.getType().equals("i32") || var.getType().contains("i32"))
            type = var.getType();   // keeps field type
        else
            type = "i8*";   // converts field(object) type to i8*

        this.writeString("\t%_"+reg_num+++" = load "+type+", "+type+"* "+load_name+'\n');
        return "%_"+(reg_num-1);    // returns where value was loaded
    }

    //TODO: IF, AND, WHILE, ARRAYLENGTH, NOTEXPRESSION
    //TODO: END: accept paths in input files
}
