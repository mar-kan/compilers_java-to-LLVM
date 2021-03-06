package llvmTranslator;


import visitor.GJDepthFirst;
import java.io.IOException;
import syntaxtree.*;
import symbols.*;


public class LlvmVisitor extends GJDepthFirst<String, String> {

    private LlvmOutput translator;
    private AllClasses allClasses;
    private Utils utils = new Utils();


    public LlvmVisitor(String filename, AllClasses classes) throws IOException
    {
        translator = new LlvmOutput(filename, classes);
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
        n.f11.accept(this, argu);

        translator.writeString("define i32 @main() {\n");

        if (n.f14.present())
            n.f14.accept(this, "main");

        if (n.f15.present())
            n.f15.accept(this, "main");

        n.f16.accept(this, argu);
        n.f17.accept(this, argu);

        translator.writeString("\n\tret i32 0\n}\n");
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
        //n.f3.accept(this, classname); // field declarations are not printed
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
        //n.f5.accept(this, classname); // field declarations are not printed
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

        // stores llvm type
        if (!myType.equals("i32") && !myType.equals("i1") && !myType.contains("i32"))
            myType = "i8*";

        // writes method declaration
        translator.writeMethodDeclaration(classname, myName, myType, args);

        // method content
        n.f7.accept(this, classname+"."+myName);
        n.f8.accept(this, classname+"."+myName);
        String return_exp = n.f10.accept(this, classname+"."+myName);

        /** checking if a field is returned **/
        if (allClasses.varIsField(return_exp, classname+"."+myName))
        {
            // loads its ptr
            VariableData var = allClasses.findVariable(return_exp, classname+"."+myName);
            return_exp = translator.writeLoadField(var);

            // loads its value
            return_exp = translator.writeLoadValue(var, return_exp, classname);
        }
        else
        {
            // loads variable if necessary
            VariableData var = allClasses.findVariable(return_exp, classname+"."+myName);
            if (var != null)
                return_exp = translator.writeLoadValue(var, var.getName(), classname);
        }

        // changing boolean values to 0 and 1
        if (return_exp.equals("true"))
            return_exp = "1";
        else if (return_exp.equals("false"))
            return_exp = "0";

        /** writes method's return expression and closes it **/
        translator.writeString("\n\tret "+myType+" "+return_exp+"\n}\n");

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

        // stores llvm type
        if (allClasses.searchClass(type) != null)
            type = "i8*";

        // allocating variable
        translator.writeString("\t"+id+" = alloca "+type+'\n');

        // initialization for ints and booleans
        if (type.equals("i32") || type.equals("i1"))
            translator.writeString("\tstore "+type+" 0, "+type+"* "+id+"\n");

        translator.writeString("\n");
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

        if (!type.equals("i32") && !type.equals("i1") && !type.contains("i32"))
            type = "i8*";

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
        String expr = n.f2.accept(this, scope);

        // removes class name if it is there
        if (expr.contains(","))
            expr = expr.substring(0, expr.indexOf(","));

        /** changing boolean values to 0 and 1 **/
        expr = utils.replaceBoolean(expr);

        translator.writeAssignment(id, expr, scope);
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
        String arrayName = '%'+n.f0.accept(this, scope);
        String index = n.f2.accept(this, scope);
        String expr = n.f5.accept(this, scope);

        if (!utils.isIntegerLiteral(expr))
            expr = "%"+expr;

        VariableData array_var = allClasses.findVariable(arrayName, scope);
        assert array_var != null;

        // loads it if it's a field
        if (allClasses.varIsField(arrayName, scope))
            arrayName = translator.writeLoadField(array_var);

        if (!arrayName.contains("%"))
            arrayName = '%'+arrayName;

        translator.writeArrayAssignment(arrayName, index, expr, scope);
        return arrayName+"["+index+"]="+expr;
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
        if (expr.contains(",")) // removes class name from expr if it exists
            expr = expr.substring(0, expr.indexOf(","));

        VariableData var = allClasses.findVariable(expr, scope);
        if (var != null)   // is a variable
            expr = translator.writeLoadValue(var, expr, scope);

        translator.writeIfStart(expr);

        translator.writeIfElseStart();
        String else_expr = n.f6.accept(this, scope);
        translator.goToIfEnd();

        translator.writeIfThenStart();
        String then_expr = n.f4.accept(this, scope);
        translator.goToIfEnd();

        translator.writeIfEnd();

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
        if (expr.contains(","))
            expr = expr.substring(0, expr.indexOf(","));

        int while_num = translator.writeWhileStatementStart(expr, allClasses.findVariable(expr, scope));
        n.f4.accept(this, scope);
        translator.writeWhileStatementEnd(while_num);
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
        if (expr.contains(","))
            expr = expr.substring(0, expr.indexOf(","));

        if (expr.contains(",")) // removes classname
            expr = expr.substring(0,expr.indexOf(","));

        translator.writePrintStatement(expr, scope);
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

        return translator.writeAndExpression(expr1, expr2, scope);
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

        /** changing boolean values to 0 and 1 **/
        if (expr1.equals("true"))
            expr1 = "1";
        else if (expr1.equals("false"))
            expr1 = "0";

        /** changing boolean values to 0 and 1 **/
        if (expr2.equals("true"))
            expr2 = "1";
        else if (expr2.equals("false"))
            expr2 = "0";

        return translator.writeCompareExpr(expr1, expr2, scope);
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

        return translator.writeArithmeticOperation(expr1, expr2, "and", scope);
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

        return translator.writeArithmeticOperation(expr1, expr2, "sub", scope);
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

        return translator.writeArithmeticOperation(expr1, expr2, "mul", scope);
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
        String index = n.f2.accept(this, scope);

        return translator.writeArrayLookup(arrayName, index, scope);
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
        n.f1.accept(this, scope);
        n.f2.accept(this, scope);

        if (arrayName.equals("int[]"))  // covers methods returning arrays
            return "int[]";

        return translator.writeArrayLength(arrayName);
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
        String classname;
        ClassData myClass = null;
        VariableData object = null;
        translator.writeString("\n");

        // f0 can be object, classname or <object_register,classname>
        String objectname = n.f0.accept(this, scope);
        if (objectname.contains("%_")) // is a register
        {
            classname = objectname.substring(objectname.indexOf(",")+1);
            objectname = objectname.substring(0, objectname.indexOf(","));
            myClass = allClasses.searchClass(classname);
        }
        else // is object or classname or 'this'
        {
            // checking if this
            if (objectname.equals("%this"))
            {
                if (scope.contains("."))
                    classname = scope.substring(0, scope.indexOf("."));
                else
                    classname = scope;

                myClass = allClasses.searchClass(classname);
            }
            else
                objectname = '%'+objectname;

            // checking if classname
            if (myClass == null)
                myClass = allClasses.searchClass(objectname);

            // checking if object
            if (myClass == null)
            {
                object = allClasses.findVariable(objectname, scope);
                assert object != null;

                // loads object
                objectname = translator.writeLoadValue(object, objectname, scope);
            }
            // getting object's class type
            if (myClass == null)
                myClass = allClasses.searchClass(object.getType());
        }
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

        return translator.writeMessageSend(objectname, method, method_arguments, myClass.getName())+","+myClass.getName();
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
        return "%this";
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
        if (!utils.isIntegerLiteral(size))
        {
            VariableData var = allClasses.findVariable(size, scope);
            assert var != null;

            size = String.valueOf(var.getValue());
        }

        return translator.writeArrayAlloc(size);
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

        ClassData aClass = allClasses.searchClass(id);
        assert aClass != null;

        return translator.writeObjectAlloc(aClass)+","+id;  // returns classname with register
    }

    /**
     * f0 -> "!"
     * f1 -> PrimaryExpression()
     */
    @Override
    public String visit(NotExpression n, String scope) throws Exception
    {
        String expr = n.f1.accept(this, scope);
        if (expr.contains(","))
            expr = expr.substring(0, expr.indexOf(","));

        return translator.writeNotExpr(expr, scope);
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
}

