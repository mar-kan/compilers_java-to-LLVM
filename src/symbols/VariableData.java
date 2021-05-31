package symbols;


public class VariableData {

    private final String name;
    private final String type;
    private int offset;


    public VariableData(String varname, String vartype, int offset)
    {
        this.name = varname;
        this.type = vartype;
        this.offset = offset;
    }


    /** setters / getters **/

    public String getType()
    {
        return this.type;
    }

    public String getName()
    {
        return this.name;
    }

    public int getOffset()
    {
        return offset;
    }
}
