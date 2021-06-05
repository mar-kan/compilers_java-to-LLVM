package llvmTranslator;

class Utils {

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
}