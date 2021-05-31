package symbols;

import myVisitors.evaluators.DeclarationEvaluator;

import java.util.LinkedList;
import java.util.Stack;


public class ClassData {

    private final String name;
    private final ClassData extending;
    private LinkedList<VariableData> fields;
    private LinkedList<MethodData> methods;

    private int field_offsets = 0;
    private int method_offsets = 0;

    public ClassData(String class_name, ClassData extend)
    {
        this.name = class_name;
        this.extending = extend;
        this.fields = new LinkedList<>();
        this.methods = new LinkedList<>();

        if (extend != null)
        {
            this.field_offsets = extend.field_offsets;
            this.method_offsets = extend.method_offsets;
        }
    }

    /** adds a field in list fields **/
    public void addField(String var_name, String var_type)
    {
        this.fields.add(new VariableData(var_name, var_type, field_offsets));
        this.field_offsets = updateOffset(field_offsets, var_type);
    }

    /** adds a method in list methods
     * myName, myType, argumentList, classname**/
    public void addMethod(String name, String type, String argumentList, String classname) throws Exception {
        MethodData method = new MethodData(name, type, argumentList, classname, method_offsets);
        this.methods.add(method);
        this.method_offsets += 8;

        DeclarationEvaluator eval = new DeclarationEvaluator();
        if (this.extending != null)
            eval.checkMethodOverriding(method, this);
    }

    /** searches list of variables for one named <varname> **/
    public VariableData searchVariable(String varname)
    {
        for (VariableData var : fields)
        {
            if (var.getName().equals(varname))
                return var;
        }

        if (extending != null)  // checks superclass' fields
            return extending.searchVariable(varname);

        return null;
    }

    /** searches list of methods for one named <methodname> **/
    public MethodData searchMethod(String methodname)
    {
        for (MethodData method : methods)
        {
            if (method.getName().equals(methodname))
                return method;
        }

        if (extending != null)  // checks superclass' fields
            return extending.searchMethod(methodname);

        return null;
    }

    /** prints offsets of class' fields **/
    public int printVarOffsets(String classname, int offset)
    {
        for(VariableData var : fields)
        {
            System.out.println(classname + "." + var.getName() + " : " + offset);
            offset = updateOffset(offset, var.getType());
        }
        return offset;
    }

    /** prints offsets of class' methods **/
    public int printMethodOffsets(String classname, int offset)
    {
        for(MethodData method : methods)
        {
            if (method.isOverriding())
                continue;

            System.out.println(classname + "." + method.getName() + " : " + offset);
            offset += 8;
        }
        return offset;
    }

    /** check for object types of inherited classes **/
    public boolean checkInheritance(String classname)
    {
        ClassData ext = extending;
        while (ext != null)
        {
            if (extending.getName().equals(classname))
                return true;

            ext = ext.getExtending();
        }

        return false;
    }

    /** updates offset according to var's type **/
    public int updateOffset(int offset, String type)
    {
        if (type.equals("int"))
            return offset+4;
        else if (type.equals("boolean"))
            return ++offset;
        else
            return offset+8;
    }

    public int getMethodSizeWithExtending()
    {
        if (this.extending == null)
            return method_offsets / 8;

        int count = method_offsets / 8;
        for (MethodData method : this.extending.getMethods())
        {
            if (method.isOverridden())
                count -= 1;
        }
        return count;
    }

    public int getLastFieldOffset()
    {
        VariableData lastField;

        if (fields.size() == 0)
        {
            if (field_offsets == 0)
                return 0;

            lastField = extending.fields.get(extending.fields.size()-1);
        }
        else
            lastField = fields.get(fields.size()-1);

        if (lastField.getType().equals("i32"))
            return field_offsets - 4;
        if (lastField.getType().equals("i1"))
            return field_offsets - 1;

        return field_offsets - 8;
    }

    /** setters and getters **/
    public String getName()
    {
        return this.name;
    }

    public ClassData getExtending()
    {
        return extending;
    }

    public LinkedList<MethodData> getMethods()
    {
        return methods;
    }

    public LinkedList<VariableData> getFields()
    {
        return fields;
    }

    public int getField_offsets()
    {
        return field_offsets;
    }

    public int getMethod_offsets()
    {
        return method_offsets;
    }
}