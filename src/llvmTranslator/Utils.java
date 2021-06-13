package llvmTranslator;

class Utils {

    /** Checks if str is an integer **/
    public boolean isIntegerLiteral(String str)
    {
        for (int i=0; i<str.length(); i++)
        {
            if (!Character.isDigit(str.charAt(0)))
                return false;
        }
        return true;
    }

    /** Checks if str is a boolean **/
    public boolean isBooleanLiteral(String str)
    {
        return str.equals("true") || str.equals("false");
    }

    /** replaces literal booleans with 0 or 1 **/
    public String replaceBoolean(String value)
    {
        if (value.equals("true"))
            value = "1";
        else if (value.equals("false"))
            value = "0";

        return value;
    }
}