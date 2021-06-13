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

    private int var_reg=0;
    private int if_reg=0;
    private int while_reg=0;
    private int oob_reg=0;
    private int nsz_reg=0;
    private int and_reg=0;


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

    /** writes the boilerplate **/
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
            int method_num = aClass.getMethodSizeWithExtending();

            writer.write("@."+aClass.getName()+"_vtable = global ["+method_num+" x i8*] ["); // writes class vtable
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

            String type = changeClassType(method.getType());
            writer.write("\ti8* bitcast ("+type+" (i8*");
            wrote = true;

            if (method.getArguments() != null)
            {
                for (VariableData argument : method.getArguments())
                {
                    type = changeClassType(argument.getType());
                    writer.write(", "+type);
                }
            }
            writer.write(")* @"+aClass.getName()+"."+method.getName()+" to i8*)");
        }
        writer.flush();
        writer.close();

        return wrote;   // returns if anything was written in the file
    }

    /** translates a method declarations **/
    public void writeMethodDeclaration(String classname, String methodName, String methodType, String args) throws IOException
    {
        this.resetAllRegisters(); // sets all registers to 0 for each scope

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
    }

    /** translates assignment expressions **/
    public void writeAssignment(String id, String expr, String scope) throws IOException
    {
        // finding variable of id
        VariableData var = allClasses.findVariable(id, scope);
        assert var != null;

        /** expr is a variable **/
        VariableData expr_var = allClasses.findVariable("%"+expr, scope);
        if (expr_var != null)
        {
            expr = this.writeLoadValue(expr_var, expr, scope);
            var.setValue(expr_var.getValue());  // changes value of id's var
        }
        else if (!expr.contains("%_"))
            var.setValue(expr);

        /** var is a field **/
        if (allClasses.varIsField(var.getName(), scope))
            id = this.writeLoadField(var);

        // assignment
        if (var.getType().equals("i32") || var.getType().equals("i32*") || var.getType().equals("i1"))
            this.writeString("\tstore "+var.getType()+" "+expr+", "+var.getType()+"* "+id+'\n');
        else
            this.writeString("\tstore i8* "+expr+", i8** "+id+'\n');
    }

    /** translates array assignments **/
    public void writeArrayAssignment(String arrayName, String index, String expr, String scope) throws IOException
    {
        String array_address = "%_"+var_reg++;  // stores the address of the array
        this.writeString("\t"+array_address+" = load i32*, i32** "+arrayName+"\n"+
                "\t%_"+(var_reg++)+" = load i32, i32* %_"+(var_reg-2)+"\n");

        this.writeOobCheck(index, scope);

        // index takes its literal value if it is a variable
        if (!utils.isIntegerLiteral(index) && !index.contains("%_"))
        {
            VariableData var = allClasses.findVariable(index, scope);
            assert var != null;

            index = String.valueOf(var.getValue());
        }

        // finds index and stores expr in id[index]
        this.writeString("\t%_"+(var_reg++)+" = add i32 1, "+index+"\n"+
                "\t%_"+(var_reg++)+" = getelementptr i32, i32* "+array_address+", i32 %_"+(var_reg-2)+'\n'+
                "\tstore i32 "+(Integer.parseInt(index)+1)+", i32* %_"+(var_reg-1)+"\n\n");
    }

    /** writes the check for oob errors of index **/
    public String writeOobCheck(String index, String scope) throws IOException
    {
        // loads index var if it is a variable
        index = writeLoadValue(index, scope);

        // checks index for errors
        this.writeString("\t%_"+(var_reg++)+" = icmp sge i32 "+index+", 0\n"+
                "\t%_"+(var_reg++)+" = icmp slt i32 "+index+", %_"+(var_reg-3)+'\n'+
                "\t%_"+(var_reg++)+" = and i1 %_"+(var_reg-3)+", %_"+(var_reg-2)+'\n'+
                "\tbr i1 %_"+(var_reg-1)+", label %oob_ok_"+oob_reg+", label %oob_err_"+oob_reg+"\n\n"+
                "\toob_err_"+oob_reg+":\n" +
                "\tcall void @throw_oob()\n"+
                "\tbr label %oob_ok_"+oob_reg+"\n\n" +
                "\toob_ok_"+(oob_reg++)+":\n");

        return index;
    }

    /** translates an if statement **/
    public void writeIfStart(String expr) throws IOException
    {
        this.writeString("\tbr i1 "+expr+", label %if_then_"+if_reg+", label %if_else_"+if_reg+"\n\n");
    }

    /** writes then part of the if **/
    public void writeIfThenStart() throws IOException
    {
        this.writeString("\tif_then_"+if_reg+":\n");
    }

    /** writes else part of the if **/
    public void writeIfElseStart() throws IOException
    {
        this.writeString("\tif_else_"+if_reg+":\n");
    }

    /** writes closing of the if **/
    public void goToIfEnd() throws IOException
    {
        this.writeString("\tbr label %if_end_"+if_reg+"\n\n");
    }

    public void writeIfEnd() throws IOException
    {
        this.writeString("\tif_end_"+(if_reg++)+":\n");
    }

    /** translates an and expression **/
    public String writeAndExpression(String expr1, String expr2, String scope) throws IOException
    {
        /** loading expr1's value if necessary */
        expr1 = this.writeLoadValue(expr1, scope);
        /** changing literal boolean values to 0 or 1 **/
        expr1 = utils.replaceBoolean(expr1);

        int label1_num = and_reg;
        this.writeString("\tbr i1 "+expr1+", label %exp_res_"+(label1_num+1)+", label %exp_res_"+label1_num+"\n\n"+
                "\texp_res_"+(and_reg++)+":\n" +
                "\tbr label %exp_res_"+(and_reg+2)+"\n\n"+
                "\texp_res_"+and_reg+":\n");

        /** loading expr2's var if necessary */
        expr2 = this.writeLoadValue(expr2, scope);
        /** changing literal boolean values to 0 or 1 **/
        expr2 = utils.replaceBoolean(expr2);

        this.writeString("\tbr label %exp_res_"+(++and_reg)+"\n\n"+
                "\texp_res_"+(and_reg++)+":\n"+
                "\tbr label %exp_res_"+and_reg+"\n\n" +
                "\texp_res_"+and_reg+++":\n"+
                "\t%_"+var_reg+" = phi i1 [ 0, %exp_res_"+label1_num+" ], [ "+expr2+", %exp_res_"+(label1_num+2)+" ]\n"
                );

        return "%_"+var_reg++;
    }

    /** translates 1st part of while statements **/
    public int writeWhileStatementStart(String expr, VariableData expr_var) throws IOException
    {
        int while_num = while_reg++;
        this.writeString("\tbr label %loop_start_"+while_num+"\n\n" +
                "\tloop_start_"+while_num+":\n");

        if (expr_var != null)   // is a variable
        {
            // loads it

            if (!expr.contains("%"))
                expr = "%"+expr;

            String type = changeClassType(expr_var.getType());
            this.writeString("\t%_"+var_reg+++" = load "+type+", "+type+"* "+expr+"\n");
            expr = "%_"+(var_reg-1);
        }

        this.writeString("\tbr i1 "+expr+", label %loop_next_"+while_num+", label %loop_end_"+while_num+'\n'+
                "\tloop_next_"+while_num+":\n");

        return while_num;
    }

    /** writes next part of the while statement **/
    public void writeWhileStatementEnd(int while_num) throws IOException
    {
        this.writeString("\tbr label %loop_start_"+while_num+"\n\n"+
                "\tloop_end_"+while_num+":\n");
    }

    /** translates print statements **/
    public void writePrintStatement(String expr, String scope) throws IOException
    {
        /** loads first if necessary **/
        expr = this.writeLoadValue(expr, scope);

        /** prints expr **/
        this.writeString("\tcall void (i32) @print_int(i32 "+expr+")\n");
    }

    /** translates compare expressions **/
    public String writeCompareExpr(String expr1, String expr2, String scope) throws IOException
    {
        // loads values first if they are variables
        expr1 = this.writeLoadValue(expr1, scope);
        expr2 = this.writeLoadValue(expr2, scope);

        String expr_reg = "%_"+var_reg++;
        this.writeString("\t"+expr_reg+" = icmp slt i32 "+expr1+", "+expr2+'\n');

        return expr_reg;
    }

    /** translates all arithmetical operations **/
    public String writeArithmeticOperation(String expr1, String expr2, String operation, String scope) throws Exception
    {
        // removes classnames if there are any
        if (expr1.contains(","))
            expr1 = expr1.substring(0, expr1.indexOf(","));
        if (expr2.contains(","))
            expr2 = expr2.substring(0, expr2.indexOf(","));

        // loads values first if they are variables
        expr1 = this.writeLoadValue(expr1, scope);
        expr2 = this.writeLoadValue(expr2, scope);

        /** writes operation **/
        this.writeString("\t%_"+var_reg+" = "+operation+" i32 "+expr1+", "+expr2+"\n");

        return "%_"+var_reg++;    // returns the register of the result
    }
    
    /** translates not expression **/
    public String writeNotExpr(String expr, String scope) throws IOException
    {
        // loads values first if expr is a variables
        expr = this.writeLoadValue(expr, scope);

        /** changing boolean values to 0 and 1 **/
        if (expr.equals("true"))
            expr = "1";
        else if (expr.equals("false"))
            expr = "0";

        /** performs not with a xor with 1 **/
        this.writeString("\t%_"+var_reg+" = xor i1 1, "+expr+"\n");
        return "%_"+var_reg++;
    }

    /** translates array lookup **/
    public String writeArrayLookup(String arrayName, String index, String scope) throws IOException
    {
        VariableData array_var = allClasses.findVariable(arrayName, scope);
        assert array_var != null;

        // loads it if it's a field
        if (allClasses.varIsField(arrayName, scope))
            arrayName = this.writeLoadField(array_var);

        if (!arrayName.contains("%"))
            arrayName = '%'+arrayName;

        String array_reg = "%_"+var_reg++;
        this.writeString("\t"+array_reg+" = load i32*, i32** "+arrayName+"\n" +
                "\t%_"+var_reg+++" = load i32, i32* %_"+(var_reg-2)+"\n");

        index = this.writeOobCheck(index, scope);

        // loads index if it is a variable
        index = this.writeLoadValue(index, scope);

        this.writeString("\t%_"+var_reg+++" = add i32 1, "+index+"\n" +
                "\t%_"+var_reg+++" = getelementptr i32, i32* "+array_reg+", i32 %_"+(var_reg-2)+"\n" +
                "\t%_"+var_reg+" = load i32, i32* %_"+(var_reg-1)+"\n\n");

        return "%_"+var_reg++;
    }
    
    /** translates array length **/
    public String writeArrayLength(String arrayName) throws IOException
    {
        this.writeString("\t"+var_reg+" = load i32, i32* "+arrayName+"\n");
        return "%_"+var_reg++;
    }

    /** translates method calls **/
    public String writeMessageSend(String objectPtr, MethodData method, String args, String classname) throws IOException
    {
        this.writeString("\t%_"+var_reg+++" = bitcast i8* "+objectPtr+" to i8***\n"+
                "\t%_"+var_reg+++" = load i8**, i8*** %_"+(var_reg-2)+'\n'+
                "\t%_"+var_reg+++" = getelementptr i8*, i8** %_"+(var_reg-2)+", i32 "+method.getOffset()/8+"\n"+
                "\t%_"+var_reg+" = load i8*, i8** %_"+(var_reg-1)+'\n');

        String load_reg = "%_"+var_reg++;
        String type = changeClassType(method.getType());

        if (args.equals(""))
            this.writeString("\t%_"+var_reg+++" = bitcast i8* %_"+(var_reg-2)+" to "+type+" (i8*)*\n" +
                    "\t%_"+var_reg+++" = call "+type+" %_"+(var_reg-2)+"(i8* "+objectPtr+")\n");
        else
        {
            String[] split_args = args.split(" ");
            StringBuilder types_args = new StringBuilder();
            StringBuilder call_args = new StringBuilder();
            for (int i=0; i<split_args.length; i++)
            {
                if (split_args[i].contains(","))    // removes commas
                    split_args[i] = split_args[i].substring(0, split_args[i].indexOf(","));

                String arg_type = changeClassType(method.getArguments().get(i).getType());

                /** loads any argument necessary **/
                if (!split_args[i].contains("%") && !utils.isBooleanLiteral(split_args[i]) && !utils.isIntegerLiteral(split_args[i]))
                {
                    if (allClasses.varIsField(split_args[i], classname+"."+method.getName()))
                        split_args[i] = writeLoadField(allClasses.findVariable(split_args[i], classname+"."+method.getName()));

                    if (!split_args[i].contains("%"))
                        split_args[i] = '%'+split_args[i];

                    this.writeString("\t%_"+var_reg+" = load "+arg_type+", "+arg_type+"* "+split_args[i]+'\n');
                    split_args[i] = "%_"+var_reg++;
                }

                /** builds a string of args (with types) for the method call **/
                if (!call_args.toString().equals(""))
                    call_args.append(",");
                call_args.append(arg_type).append(" ").append(split_args[i]);

                /** builds a string of types of args for the method types **/
                if (!types_args.toString().equals(""))
                    types_args.append(",");
                types_args.append(arg_type);
            }

            type = changeClassType(method.getType());
            this.writeString("\t%_"+var_reg+++" = bitcast i8* "+load_reg+" to "+type+" (i8*,"+types_args+")*\n" +
                    "\t%_"+var_reg+++" = call "+type+" %_"+(var_reg-2)+"(i8* "+objectPtr+", "+call_args+")\n");
        }
        return "%_"+(var_reg-1);    // returns call receiver register
    }

    /** translates array allocations **/
    public String writeArrayAlloc(String size) throws IOException
    {
        this.writeString("\t%_"+(var_reg++)+" = add i32 1, "+ size+'\n');

        // check that size is >= 1
        this.writeString("\t%_"+var_reg+++" = icmp sge i32 %_"+(var_reg-2)+", 1\n" +
                "\tbr i1 %_"+(var_reg-1)+", label %nsz_ok_"+nsz_reg+", label %nsz_err_"+nsz_reg+"\n\n" +
                "\tnsz_err_"+nsz_reg+":\n"+
                "\tcall void @throw_nsz()\n" +
                "\tbr label %nsz_ok_"+if_reg+"\n\n" +
                "\tnsz_ok_"+(nsz_reg++)+":\n");

        // allocation
        this.writeString("\t%_"+(var_reg++)+" = call i8* @calloc(i32 %_"+(var_reg-3)+", i32 4)\n"+
                "\t%_"+(var_reg++)+" = bitcast i8* %_"+(var_reg-2)+" to i32*\n\t" +
                "store i32 2, i32* %_"+(var_reg-1)+"\n\n");

        return "%_"+(var_reg - 1);  // returns register of ptr to array
    }

    /** translates object allocations **/
    public String writeObjectAlloc(ClassData aClass) throws IOException
    {
        int bytes = 8 /* v-table */ + aClass.getField_offsets() /* offsets including last field's*/;
        int object_reg = var_reg++;
        this.writeString("\t%_"+object_reg+" = call i8* @calloc(i32 1, i32 "+bytes+")\n");

        int method_num = aClass.getMethodSizeWithExtending();

        String vtablePtr = "%_"+var_reg++;
        this.writeString("\t"+vtablePtr+" = bitcast i8* %_"+(var_reg-2)+" to i8***\n"+
                "\t%_"+var_reg+" = getelementptr ["+method_num+" x i8*], ["+method_num+" x i8*]* @."+aClass.getName()+"_vtable, i32 0, i32 0\n"+
                "\tstore i8** %_"+(var_reg++)+", i8*** "+vtablePtr+"\n");

        return "%_"+object_reg; // returns register of ptr to object
    }


    /** writes the instructions required to load a field in a register before using it **/
    public String writeLoadField(VariableData var) throws IOException
    {
        String type = changeClassType(var.getType());

        this.writeString("\t%_"+var_reg+++" = getelementptr i8, i8* %this, i32 "+(var.getOffset()+8)+"\n"
                +"\t%_"+var_reg+++" = bitcast i8* %_"+(var_reg-2)+" to "+type+"*\n");

        return "%_"+(var_reg-1);  // returns the num of register of ptr to this->var
    }

    /** writes the load of a variable's value depending on the var object. it always loads **/
    public String writeLoadValue(VariableData var, String load_name, String scope) throws IOException
    {
        String type = changeClassType(var.getType());
        if (!load_name.contains("%"))
            load_name = '%'+load_name;

        if (allClasses.varIsField(var.getName(), scope))
            load_name = writeLoadField(var);

        this.writeString("\t%_"+var_reg+++" = load "+type+", "+type+"* "+load_name+'\n');
        return "%_"+(var_reg-1);    // returns where value was loaded
    }

    /** same as above but without a var object. doesn't always load **/
    public String writeLoadValue(String var_name, String scope) throws IOException
    {
        VariableData var = allClasses.findVariable(var_name, scope);
        if (var == null)    // it's not a variable
            return var_name;

        String type = changeClassType(var.getType());
        if (!var_name.contains("%"))
            var_name = '%'+var_name;

        if (allClasses.varIsField(var_name, scope))
            var_name = writeLoadField(var);

        this.writeString("\t%_"+var_reg+++" = load "+type+", "+type+"* "+var_name+'\n');
        return "%_"+(var_reg-1);    // returns where value was loaded
    }


    /** resets all registers to 0 **/
    public void resetAllRegisters()
    {
        var_reg = 0;
        and_reg = 0;
        if_reg = 0;
        nsz_reg = 0;
        oob_reg = 0;
        while_reg = 0;
    }

    /** changes class type to i8* **/
    public String changeClassType(String type)
    {
        if (!type.equals("i32") && !type.equals("i32*") && !type.equals("i1"))
            type = "i8*";

        return type;
    }

    //TODO: WHILE
    //TODO: END: accept paths in input files
}
