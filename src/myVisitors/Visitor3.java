package myVisitors;


import visitor.GJDepthFirst;
import java.io.IOException;
import syntaxtree.*;
import symbols.*;


public class Visitor3 extends GJDepthFirst<String, String> {

    private OutputFile output;
    private AllClasses allClasses;
    private int reg_num=0;
    private int if_num=0;
    private int while_num=0;
    private int oob_num=0;
    private int nsz_num=0;
    private int and_num=0;


    public Visitor3(String filename, AllClasses classes) throws IOException
    {
        output = new OutputFile(filename, classes);
        allClasses = classes;
    }


    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> "public"
     * f4 -> "static"
     * f5 -> "void"
     * f6 -> "main"
     * f7 -> "("
     * f8 -> "String"
     * f9 -> "["
     * f10 -> "]"
     * f11 -> Identifier()
     * f12 -> ")"
     * f13 -> "{"
     * f14 -> ( VarDeclaration() )*
     * f15 -> ( Statement() )*
     * f16 -> "}"
     * f17 -> "}"
     */
    @Override
    public String visit(MainClass n, String argu) throws Exception
    {
        n.f0.accept(this, argu);
        String classname = n.f1.accept(this, "main");

        String argname = n.f11.accept(this, argu);
        if (argname == null)
            output.writeString("define i32 @main() {\n");
        else
            output.writeString("define i32 @main() {\n");

        if (n.f14.present())
            n.f14.accept(this, "main");

        if (n.f15.present())
            n.f15.accept(this, "main");

        n.f16.accept(this, argu);
        n.f17.accept(this, argu);

        output.writeString("\n\tret i32 0\n}\n");
        return classname;
    }

    /**
     * f0 -> ClassDeclaration()
     *       | ClassExtendsDeclaration()
     */
    @Override
    public String visit(TypeDeclaration n, String argu) throws Exception
    {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> ( VarDeclaration() )*
     * f4 -> ( MethodDeclaration() )*
     * f5 -> "}"
     */
    @Override
    public String visit(ClassDeclaration n, String argu) throws Exception
    {
        String classname = n.f1.accept(this, null);
        n.f4.accept(this, classname);

        return classname;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "extends"
     * f3 -> Identifier()
     * f4 -> "{"
     * f5 -> ( VarDeclaration() )*
     * f6 -> ( MethodDeclaration() )*
     * f7 -> "}"
     */
    @Override
    public String visit(ClassExtendsDeclaration n, String argu) throws Exception
    {
        String classname = n.f1.accept(this, null);
        n.f3.accept(this, classname);
        n.f6.accept(this, classname);

        return classname;
    }

    /**
     * f0 -> "public"
     * f1 -> Type()
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( FormalParameterList() )?
     * f5 -> ")"
     * f6 -> "{"
     * f7 -> ( VarDeclaration() )*
     * f8 -> ( Statement() )*
     * f9 -> "return"
     * f10 -> Expression()
     * f11 -> ";"
     * f12 -> "}"
     */
    @Override
    public String visit(MethodDeclaration n, String classname) throws Exception
    {
        String myType = n.f1.accept(this, classname);
        String myName = n.f2.accept(this, classname);
        String args = n.f4.accept(this, classname+"."+myName);

        reg_num = 0;    // starting registers again from 0 in every scope

        // writing method declaration
        if (args == null)
            output.writeString("\ndefine "+myType+" @"+classname+"."+myName+"(i8* %this) {\n");
        else
        {
            output.writeString("\ndefine "+myType+" @"+classname+"."+myName+"(i8* %this, "+args+") {\n");
            String[] split_args = args.split(" ");
            for (int i=1; i<split_args.length; i+=2)
            {
                if (split_args[i].contains(","))    // removes commas
                    split_args[i] = split_args[i].substring(0, split_args[i].indexOf(","));

                // allocating and storing every argument in variable
                output.writeString("\t%"+split_args[i].substring(split_args[i].indexOf(".")+1)+" = alloca "+split_args[i-1]+'\n'+
                        "\tstore "+split_args[i-1]+" "+split_args[i]+", "+split_args[i-1]+"* %"+split_args[i].substring(split_args[i].indexOf(".")+1)+"\n\n");
            }
        }

        n.f7.accept(this, classname+"."+myName);
        n.f8.accept(this, classname+"."+myName);

        String return_exp = n.f10.accept(this, classname+"."+myName);

        /** returning a field **/
        if (allClasses.varIsField(return_exp, classname+"."+myName))
        {
            VariableData var = allClasses.findVariable(return_exp, classname+"."+myName);
            return_exp = loadField(var);

            output.writeString("\t%_"+reg_num+" = load "+var.getType()+", "+var.getType()+"* "+return_exp+'\n');
            return_exp = "%_"+reg_num++;
        }


        output.writeString("\n\tret "+myType+" "+return_exp+"\n}\n");

        return null;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    @Override
    public String visit(VarDeclaration n, String scope) throws Exception
    {
        String type = n.f0.accept(this, scope);
        String id = '%'+n.f1.accept(this, scope);

        /** skipping fields of classes **/
        if (allClasses.searchClass(scope) != null)
            return type;

        if (allClasses.searchClass(type) != null)
            type = "i8*";

        // allocating variable
        output.writeString("\t"+id+" = alloca "+type+'\n');

        // initialization for ints and booleans
        if (type.equals("i32") || type.equals("i1"))
            output.writeString("\tstore "+type+" 0, "+type+"* "+id+"\n\n");

        return type;
    }


    /******** arguments ********

    /**
     * f0 -> FormalParameter()
     * f1 -> FormalParameterTail()
     */
    @Override
    public String visit(FormalParameterList n, String scope) throws Exception
    {
        String ret = n.f0.accept(this, scope);

        if (n.f1 != null)
            ret += n.f1.accept(this, scope);

        return ret;
    }

    /**
     * f0 -> FormalParameter()
     * f1 -> FormalParameterTail()
     */
    @Override
    public String visit(FormalParameterTerm n, String scope) throws Exception
    {
        return n.f1.accept(this, scope);
    }

    /**
     * f0 -> ","
     * f1 -> FormalParameter()
     */
    @Override
    public String visit(FormalParameterTail n, String scope) throws Exception
    {
        StringBuilder ret = new StringBuilder();
        for (Node node: n.f0.nodes)
        {
            ret.append(", ").append(node.accept(this, scope));
        }

        return ret.toString();
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     */
    @Override
    public String visit(FormalParameter n, String scope) throws Exception
    {
        String type = n.f0.accept(this, scope);
        String name = n.f1.accept(this, scope);
        return type+" %."+name;
    }


    /******** statements ********

    /**
     * f0 -> Block()
     *       | AssignmentStatement()
     *       | ArrayAssignmentStatement()
     *       | IfStatement()
     *       | WhileStatement()
     *       | PrintStatement()
     */
    @Override
    public String visit(Statement n, String argu) throws Exception
    {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> "{"
     * f1 -> ( Statement() )*
     * f2 -> "}"
     */
    @Override
    public String visit(Block n, String scope) throws Exception
    {
        for (Node node: n.f1.nodes)
        {
            node.accept(this, scope);
        }
        return null;
    }

    /**
     * f0 -> Identifier()
     * f1 -> "="
     * f2 -> Expression()
     * f3 -> ";"
     */
    @Override
    public String visit(AssignmentStatement n, String scope) throws Exception
    {
        String id = '%'+n.f0.accept(this, scope);
        String expr = n.f2.accept(this, scope);    // passes var to be assigned

        /** changing boolean values to 0 and 1 **/
        if (expr.equals("true"))
            expr = "1";
        else if (expr.equals("false"))
            expr = "0";

        // finding variable of id
        VariableData var = allClasses.findVariable(id, scope);
        assert var != null;

        /** expr is a variable **/
        VariableData variable = allClasses.findVariable("%"+expr, scope);
        if (variable != null)
        {
            // loading expr
            if (var.getType().equals("i32") || var.getType().contains("i32") || var.getType().equals("i1"))
                output.writeString("\t%_"+reg_num+++" = load "+variable.getType()+", "+variable.getType()+"* %"+expr+'\n');
            else
                output.writeString("\t%_"+reg_num+++" = load i8*, i8** %"+expr+'\n');

            expr = "%_"+(reg_num-1);
        }

        /** var is a field **/
        if (allClasses.varIsField(var.getName(), scope))
            id = loadField(var);

        // assignment
        if (var.getType().equals("i32") || var.getType().contains("i32") || var.getType().equals("i1"))
            output.writeString("\tstore "+var.getType()+" "+expr+", "+var.getType()+"* "+id+'\n');
        else
            output.writeString("\tstore i8* "+expr+", i8** "+id+'\n');

        return id+"="+expr;
    }

    /**
     * f0 -> Identifier()
     * f1 -> "["
     * f2 -> Expression()
     * f3 -> "]"
     * f4 -> "="
     * f5 -> Expression()
     * f6 -> ";"
     */
    @Override
    public String visit(ArrayAssignmentStatement n, String scope) throws Exception
    {
        String id = '%'+n.f0.accept(this, scope);
        String index = n.f2.accept(this, scope);
        String expr = n.f5.accept(this, scope);

        if (!isIntegerLiteral(expr))
            expr = "%"+expr;

        String array_address = "%_"+reg_num++;  // stores the address of the array
        output.writeString("\t"+array_address+" = load i32*, i32** "+id+"\n"+
                "\t%_"+(reg_num++)+" = load i32, i32* %_"+(reg_num-2)+"\n");

        // checks index for errors
        output.writeString("\t%_"+(reg_num++)+" = icmp sge i32 "+index+", 0\n"+
                "\t%_"+(reg_num++)+" = icmp slt i32 "+index+", %_"+(reg_num-3)+'\n'+
                "\t%_"+(reg_num++)+" = and i1 %_"+(reg_num-3)+", %_"+(reg_num-2)+'\n'+
                "\tbr i1 %_"+(reg_num-1)+", label %oob_ok_"+oob_num+", label %oob_err_"+oob_num+"\n\n"+
                "\toob_err_"+oob_num+":\n\tcall void @throw_oob()\n"+
                "\tbr label %oob_ok_"+oob_num+"\n\n\toob_ok_"+(oob_num++)+":\n");

        // finds index and stores expr in id[index]
        output.writeString("\t%_"+(reg_num++)+" = add i32 1, "+index+"\n"+
                "\t%_"+(reg_num++)+" = getelementptr i32, i32* "+array_address+", i32 %_"+(reg_num-2)+'\n'+
                "\tstore i32 "+(Integer.parseInt(index)+1)+", i32* %_"+(reg_num-1)+"\n\n");

        return id+"["+index+"]="+expr;
    }

    /**
     * f0 -> "if"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     * f5 -> "else"
     * f6 -> Statement()
     */
    @Override
    public String visit(IfStatement n, String scope) throws Exception
    {
        String expr = n.f2.accept(this, scope);
        if (expr.contains("and"))   // and expressions are printed in
            return null;

        output.writeString("\tbr i1 "+expr+", label %if_then_"+if_num+", label %if_else_"+if_num+"\n\n");

        output.writeString("\tif_else_"+if_num+":\n");
        String else_expr = n.f6.accept(this, scope);
        output.writeString("\tbr label %if_end_"+if_num+"\n\n");

        output.writeString("\tif_then_"+if_num+":\n");
        String then_expr = n.f4.accept(this, scope);
        output.writeString("\tbr label %if_end_"+if_num+"\n\n");

        output.writeString("\tif_end_"+(if_num++)+":\n");

        return null;
    }

    /**
     * f0 -> "while"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     */
    @Override
    public String visit(WhileStatement n, String scope) throws Exception
    {
        String expr = n.f2.accept(this, scope);

        output.writeString("\tstore i32 0, i32* %count\n\tbr label %loopstart_"+while_num+"\n\n" +
                "\tloopstart_"+while_num+":\n"+
                "\t% = load i32, i32* %count\n"+
                "\t%fin = icmp "+expr+'\n'+
                "\tbr i1 %fin, label %next_"+while_num+", label %end_"+while_num+"\n\n"+
                "\tnext_"+while_num+":\n");

        n.f4.accept(this, scope);


        output.writeString("\tnext_i = add i32 %i, 1\n\tstore i32 %next_i, i32* %count\n"+
                "\tbr label %loopstart_"+while_num+"\n\n"+
                "\tend_"+(while_num++)+":\n\tret i32 0\n\n");

        return null;
    }

    /**
     * f0 -> "System.out.println"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> ";"
     */
    @Override
    public String visit(PrintStatement n, String scope) throws Exception
    {
        String expr = n.f2.accept(this, scope);
        if (isIntegerLiteral(expr) || expr.contains("%_"))
            output.writeString("\tcall void (i32) @print_int(i32 "+expr+")\n");
        else
            output.writeString("\t%_"+reg_num+" = load i32, i32* %"+expr+"\n\tcall void (i32) @print_int(i32 %_"+(reg_num++)+")\n");

        return null;
    }


    /******** expressions ********

    /**
     * f0 -> AndExpression()
     *       | CompareExpression()
     *       | PlusExpression()
     *       | MinusExpression()
     *       | TimesExpression()
     *       | ArrayLookup()
     *       | ArrayLength()
     *       | MessageSend()
     *       | PrimaryExpression()
     */
    @Override
    public String visit(Expression n, String argu) throws Exception
    {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "&&"
     * f2 -> PrimaryExpression()
     */
    @Override
    public String visit(AndExpression n, String scope) throws Exception
    {

        String expr1 = n.f0.accept(this, scope);
        String expr2 = n.f2.accept(this, scope);

        if (!isBooleanLiteral(expr1))
        {
            output.writeString("\t%_"+reg_num+" = load i1, i1* %"+expr1+'\n');
            expr1 = "%_"+reg_num++;
        }

        output.writeString("\tbr i1 %_"+(reg_num-1)+", label %exp_res_"+(and_num+1)+", label %exp_res_"+and_num+"\n\n"+
                "\texp_res_"+(and_num++)+":\n\tbr label %exp_res_"+(and_num+2)+"\n\n"+
                "\texp_res_"+and_num+":\n");

        if (!isBooleanLiteral(expr2))
        {
            output.writeString("\t%_"+reg_num+" = load i1, i1* %"+expr2+'\n');
            expr2 = "%_"+reg_num++;
        }
        output.writeString("\tbr label %exp_res_"+(++and_num)+"\n\n"+
                "\texp_res_"+(and_num++)+":\n\tbr label %exp_res_"+and_num+"\n");

        return expr1+" and "+expr2;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "<"
     * f2 -> PrimaryExpression()
     */
    @Override
    public String visit(CompareExpression n, String scope) throws Exception
    {
        String expr1 = n.f0.accept(this, scope);
        String expr2 = n.f2.accept(this, scope);
        output.writeString("\n");

        if (!isIntegerLiteral(expr1))
        {
            expr1 = "%"+expr1;
            output.writeString("\t%_"+reg_num+" = load i32, i32* "+expr1+'\n');
            expr1 = "%_"+reg_num++;
        }

        if (!isIntegerLiteral(expr2))
        {
            expr2 = "%"+expr2;
            output.writeString("\t%_"+(reg_num++)+" = load i32, i32* "+expr2+'\n');
            expr2 = "%_"+reg_num++;
        }
        String expr_reg = String.valueOf(reg_num++);
        output.writeString("\t%_"+expr_reg+" = icmp slt i32 "+expr1+", "+expr2+'\n');
        return "%_"+expr_reg;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "+"
     * f2 -> PrimaryExpression()
     */
    @Override
    public String visit(PlusExpression n, String scope) throws Exception
    {
        String expr1 = n.f0.accept(this, scope);
        String expr2 = n.f2.accept(this, scope);

        return arithmeticOperation(expr1, expr2, '+', scope);
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "-"
     * f2 -> PrimaryExpression()
     */
    @Override
    public String visit(MinusExpression n, String scope) throws Exception
    {
        String expr1 = n.f0.accept(this, scope);
        String expr2 = n.f2.accept(this, scope);

        return arithmeticOperation(expr1, expr2, '-', scope);
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "*"
     * f2 -> PrimaryExpression()
     */
    @Override
    public String visit(TimesExpression n, String scope) throws Exception
    {
        String expr1 = n.f0.accept(this, scope);
        String expr2 = n.f2.accept(this, scope);

        return arithmeticOperation(expr1, expr2, '*', scope);
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "["
     * f2 -> PrimaryExpression()
     * f3 -> "]"
     */
    @Override
    public String visit(ArrayLookup n, String scope) throws Exception
    {
        String arrayName = n.f0.accept(this, scope);
        VariableData array = null;//allClasses.findVariable(arrayName, scope);
        String index = n.f2.accept(this, scope);    // checks that index evaluates to int

        String array_reg = "%_"+reg_num++;
        output.writeString("\t"+array_reg+" = load i32*, i32** %"+arrayName+"\n" +
                "    %_"+reg_num+++" = load i32, i32* %_"+(reg_num-2)+"\n" +
                "    %_"+reg_num+++" = icmp sge i32 "+index+", 0\n" +
                "    %_"+reg_num+++" = icmp slt i32 "+index+", %_"+(reg_num-3)+"\n" +
                "    %_"+reg_num+++" = and i1 %_"+(reg_num-3)+", %_"+(reg_num-2)+"\n" +
                "    br i1 %_"+(reg_num-1)+", label %oob_ok_"+oob_num+", label %oob_err_"+oob_num+"\n\n" +
                "    oob_err_"+oob_num+":\n" +
                "    call void @throw_oob()\n" +
                "    br label %oob_ok_"+oob_num+"\n\n" +
                "    oob_ok_"+oob_num+++":\n" +
                "    %_"+reg_num+++" = add i32 1, "+index+"\n" +
                "    %_"+reg_num+++" = getelementptr i32, i32* "+array_reg+", i32 %_"+(reg_num-2)+"\n" +
                "    %_"+reg_num+" = load i32, i32* %_"+(reg_num-1)+"\n\n");

        return "%_"+reg_num++;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> "length"
     */
    @Override
    public String visit(ArrayLength n, String scope) throws Exception
    {
        String arrayName = n.f0.accept(this, scope);
        if (arrayName.equals("int[]"))  // covers methods returning arrays
            return "int[]";

        VariableData array = null;
        /*if (array != null)
        {
            /** array is a field **
            if (varIsField(var, scope))
                id = loadField(var);
        }*/
        return "int";
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( ExpressionList() )?
     * f5 -> ")"
     */
    @Override
    public String visit(MessageSend n, String scope) throws Exception
    {
        ClassData myClass;
        VariableData object = null;
        output.writeString("\n");

        // f0 can be object or classname
        String objectname = n.f0.accept(this, scope);

        if (objectname.contains("%_")) // is register
        {

        }

        objectname = '%'+objectname;

        // checking if classname
        myClass = allClasses.searchClass(objectname);

        // checking if object
        if (myClass == null)
        {
            // checks if it's an object
            object = allClasses.findVariable(objectname, scope);
            assert object != null;
        }
        // getting object's class type
        if (myClass == null)
            myClass = allClasses.searchClass(object.getType());
        assert myClass != null;

        // f2 can be a method of <myClass>
        String methodname = n.f2.accept(this, scope);
        MethodData method = myClass.searchMethod(methodname);
        assert method != null;

        // f4 can be any num of arguments or ""
        String method_arguments;
        if (n.f4.present())
            method_arguments = n.f4.accept(this, scope);
        else
            method_arguments = "";

        String objectPtr = "%_"+reg_num++;
        output.writeString('\t'+objectPtr+" = load i8*, i8** "+objectname+'\n'+
                "\t%_"+reg_num+++" = bitcast i8* "+objectPtr+" to i8***\n"+
                "\t%_"+reg_num+++" = load i8**, i8*** %_"+(reg_num-2)+'\n'+
                "\t%_"+reg_num+++" = getelementptr i8*, i8** %_"+(reg_num-2)+", i32 0\n"+
                "\t%_"+reg_num+++" = load i8*, i8** %_"+(reg_num-2)+'\n');

        output.writeString("\t%_"+reg_num+++" = bitcast i8* %_"+(reg_num-2)+" to "+method.getType()+" (i8*");

        if (method_arguments.equals(""))
            output.writeString(")*\n\t%_"+reg_num+++" = call "+method.getType()+" %_"+(reg_num-2)+"(i8*)\n");
        else
        {
            String[] split_args = method_arguments.split(" ");
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
            output.writeString(","+decl_args+")*\n\t%_"+reg_num+++" = call "+method.getType()+" %_"+(reg_num-2)+"(i8* "+
                    objectPtr+", "+call_args+")\n");
        }


        return "%_"+(reg_num-1);    // returns call receiver register
    }

    /**
     * f0 -> Expression()
     * f1 -> ExpressionTail()
     */
    @Override
    public String visit(ExpressionList n, String argu) throws Exception
    {
        return n.f0.accept(this, argu) + n.f1.accept(this, argu);
    }

    /**
     * f0 -> ( ExpressionTerm() )*
     */
    @Override
    public String visit(ExpressionTail n, String argu) throws Exception
    {
        StringBuilder ret = new StringBuilder();
        for (Node node: n.f0.nodes)
        {
            ret.append(", ").append(node.accept(this, argu));
        }

        return ret.toString();
    }

    /**
     * f0 -> ","
     * f1 -> Expression()
     */
    @Override
    public String visit(ExpressionTerm n, String argu) throws Exception
    {
        n.f0.accept(this, argu);
        return n.f1.accept(this, argu);
    }


    /******** primary expressions ********

    /**
     * f0 -> IntegerLiteral()
     *       | TrueLiteral()
     *       | FalseLiteral()
     *       | Identifier()
     *       | ThisExpression()
     *       | ArrayAllocationExpression()
     *       | AllocationExpression()
     *       | NotExpression()
     *       | BracketExpression()
     */
    @Override
    public String visit(PrimaryExpression n, String argu) throws Exception
    {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> <INTEGER_LITERAL>
     */
    @Override
    public String visit(IntegerLiteral n, String argu) throws Exception
    {
        return n.f0.tokenImage;
    }

    /**
     * f0 -> "true"
     */
    @Override
    public String visit(TrueLiteral n, String argu) throws Exception
    {
        return "true";
    }

    /**
     * f0 -> "false"
     */
    @Override
    public String visit(FalseLiteral n, String argu) throws Exception
    {
        return "false";
    }

    /**
     * f0 -> "this"
     */
    @Override
    public String visit(ThisExpression n, String scope) throws Exception
    {
        return "this";
    }

    /**
     * f0 -> "new"
     * f1 -> "int"
     * f2 -> "["
     * f3 -> Expression()
     * f4 -> "]"
     */
    @Override
    public String visit(ArrayAllocationExpression n, String scope) throws Exception
    {
        String size = n.f3.accept(this, scope);
        output.writeString("\t%_"+(reg_num++)+" = add i32 1, "+ size+'\n');

        // check that size is >= 1
        output.writeString("\t%_"+reg_num+++" = icmp sge i32 %_"+(reg_num-2)+", 1\n\tbr i1 %_"+(reg_num-1)+
                ", label %nsz_ok_"+nsz_num+", label %nsz_err_"+nsz_num+"\n\n\tnsz_err_"+nsz_num+":\n"+
                "\tcall void @throw_nsz()\n\tbr label %nsz_ok_"+if_num+"\n\n\tnsz_ok_"+(nsz_num++)+":\n");

        // allocation
        output.writeString("\t%_"+(reg_num++)+" = call i8* @calloc(i32 %_"+(reg_num-3)+", i32 4)\n"+
                "\t%_"+(reg_num++)+" = bitcast i8* %_"+(reg_num-2)+" to i32*\n\tstore i32 2, i32* %_"+(reg_num-1)+"\n\n");

        return "%_"+(reg_num - 1);
    }

    /**
     * f0 -> "new"
     * f1 -> Identifier()
     * f2 -> "("
     * f3 -> ")"
     */
    @Override
    public String visit(AllocationExpression n, String scope) throws Exception
    {
        String id = n.f1.accept(this, scope);
        //bytes = class offset of fields + 8 for vtable
        ClassData aClass = allClasses.searchClass(id);
        assert aClass != null;
        int bytes = 8 /* v-table */ + aClass.getLastFieldOffset() /* offset of last field*/;
        int object_reg = reg_num++;
        output.writeString("\t%_"+object_reg+" = call i8* @calloc(i32 1, i32 "+bytes+")\n");

        String vtablePtr = "%_"+reg_num++;
        output.writeString("\t"+vtablePtr+" = bitcast i8* %_"+(reg_num-2)+" to i8***\n"+
        "\t%_"+reg_num+" = getelementptr [2 x i8*], [2 x i8*]* @."+id+"_vtable, i32 0, i32 0\n"+
        "\tstore i8** %_"+(reg_num++)+", i8*** "+vtablePtr+"\n");

        return "%_"+object_reg;
    }

    /**
     * f0 -> "!"
     * f1 -> PrimaryExpression()
     */
    @Override
    public String visit(NotExpression n, String scope) throws Exception
    {
        return n.f1.accept(this, scope);
    }

    /**
     * f0 -> "("
     * f1 -> Expression()
     * f2 -> ")"
     */
    @Override
    public String visit(BracketExpression n, String argu) throws Exception
    {
        return n.f1.accept(this, argu);
    }

    /******** data types ********
    // the data types are returned in the LLVM form

    /**
     * f0 -> ArrayType()
     *       | BooleanType()
     *       | IntegerType()
     *       | Identifier()
     */
    @Override
    public String visit(Type n, String argu) throws Exception
    {
        return n.f0.accept(this, argu);
    }

    @Override
    public String visit(ArrayType n, String argu)
    {
        return "i32*";
    }

    @Override
    public String visit(BooleanType n, String argu)
    {
        return "i1";
    }

    @Override
    public String visit(IntegerType n, String argu)
    {
        return "i32";
    }


    /** ids **/
    @Override
    public String visit(Identifier n, String argu)
    {
        return n.f0.toString();
    }


    /** Other **/
    public boolean isIntegerLiteral(String str)
    {
        for (int i=0; i<str.length(); i++)
        {
            if (!Character.isDigit(str.charAt(0)))
                return false;
        }
        return true;
    }

    public boolean isBooleanLiteral(String str)
    {
        return str.equals("true") || str.equals("false");
    }

    public String loadField(VariableData var) throws IOException
    {
        output.writeString("\t%_"+reg_num+++" = getelementptr i8, i8* %this, "+var.getType()+" "+(var.getOffset()+8)+"\n"
                +"\t%_"+reg_num+++" = bitcast i8* %_"+(reg_num-2)+" to "+var.getType()+"*\n");

        return "%_"+(reg_num-1);  // returns the register of the ptr to this->var
    }

    public String arithmeticOperation(String expr1, String expr2, int op, String scope) throws Exception {
        VariableData var1 = allClasses.findVariable(expr1, scope);
        VariableData var2 = allClasses.findVariable(expr2, scope);

        if (var1 != null)
        {
            output.writeString("\t%_"+reg_num+" = load i32, i32* "+var1.getName()+"\n");
            expr1 = "%_"+reg_num++;
        }
        ;
        if (var2 != null)
        {
            output.writeString("\t%_"+reg_num+" = load i32, i32* "+var2.getName()+"\n");
            expr2 = "%_"+reg_num++;
        }

        if (op=='+')
            output.writeString("\t%_"+reg_num+" = add i32 "+expr1+", "+expr2+"\n");
        else if (op=='-')
            output.writeString("\t%_"+reg_num+" = sub i32 "+expr1+", "+expr2+"\n");
        else if (op=='*')
            output.writeString("\t%_"+reg_num+" = mul i32 "+expr1+", "+expr2+"\n");
        else if (op=='&')
            output.writeString("\t%_"+reg_num+" = and i32 "+expr1+", "+expr2+"\n");
        else
            throw new Exception("Wrong op");

        return "%_"+reg_num++;    // returns the register of the result
    }

}

