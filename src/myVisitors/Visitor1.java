package myVisitors;

import myVisitors.evaluators.DeclarationEvaluator;
import visitor.GJDepthFirst;
import syntaxtree.*;
import symbols.*;


public class Visitor1 extends GJDepthFirst<String, String> {
    /** Visitor1 checks every error related to declarations **/

    private DeclarationEvaluator declarationEvaluator = new DeclarationEvaluator();
    private AllClasses allClasses = new AllClasses();


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
        String classname = n.f1.accept(this, "main");
        allClasses.setMain_class_name(classname);               // stores main class' name

        if (n.f14.present())
            n.f14.accept(this, "main");                 // deals with declarations only

        super.visit(n, null);
        return null;
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

        // checks declaration for errors
        declarationEvaluator.checkClassName(classname, null, allClasses);
        allClasses.addClass(classname, null);    // adds new class in list of all classes

        n.f3.accept(this, classname);
        n.f4.accept(this, classname);

        super.visit(n, null);
        return null;
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
        String extend = n.f3.accept(this, classname);

        // checks declaration for errors
        declarationEvaluator.checkClassName(classname, extend, allClasses);
        allClasses.addClass(classname, allClasses.searchClass(extend)); // adds new class in list of all classes

        n.f5.accept(this, classname);
        n.f6.accept(this, classname);

        super.visit(n, null);
        return null;
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
        if (classname == null)
            return null;

        String myType = n.f1.accept(this, classname);
        String myName = n.f2.accept(this, classname);
        String argumentList = n.f4.present() ? n.f4.accept(this, classname) : "";

        // finds its class in MyClasses' list and adds the method there
        ClassData myClass = allClasses.searchClass(classname);

        myClass.addMethod(myName, myType, argumentList, classname);

        n.f7.accept(this, classname+"."+myName);    // deals only with its declarations

        super.visit(n, null);
        return null;
    }


    /******** arguments ********/
    // they are built and returned all together in a string

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
        for ( Node node: n.f0.nodes)
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

        return type+" %"+name;
    }


    /******** variables ********
    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    @Override
    public String visit(VarDeclaration n, String scope) throws Exception
    {
        if (scope == null)
            return null;

        String type = n.f0.accept(this, scope);
        String id = '%'+n.f1.accept(this, scope);

        if (scope.contains("."))    /** in method **/
        {
            String classname, method;
            classname = scope.substring(0, scope.indexOf("."));
            method = scope.substring(scope.indexOf(".")+1, scope.length());

            MethodData methodData = allClasses.searchClass(classname).searchMethod(method);
            declarationEvaluator.checkVarMethodDuplicates(id, methodData, classname);    // checks for variable duplicates
            methodData.addVariable(id, type);                                            // adds var in method of class
        }
        else if (scope.equals("main"))    /** in main **/
        {
            declarationEvaluator.checkVarMainDuplicates(id, allClasses.getMainClass());  // checks for variable duplicates
            allClasses.getMainClass().addField(id, type);                                // adds var in main
        }
        else    /** in class **/
        {
            ClassData aClass = allClasses.searchClass(scope);
            declarationEvaluator.checkFieldDuplicates(id, aClass);                       // checks for duplicate fields
            aClass.addField(id, type);                                                   // adds new field in its class
        }
        return null;
    }


    /******** primary expressions ********/

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
        String expr = n.f3.accept(this, scope);

        return "["+expr+" x i8]";
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
        n.f1.accept(this, scope);
        return "i8*";
    }


    /******** data types ********/
    // the types are returned in the LLVM form

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


    /** getters **/
    public AllClasses getAllClasses()
    {
        return allClasses;
    }
}
