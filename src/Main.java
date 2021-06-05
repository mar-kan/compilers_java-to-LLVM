import llvmTranslator.LlvmVisitor;
import myVisitors.*;
import syntaxtree.*;

import java.io.*;


public class Main {

    public static void main(String[] args) throws Exception {
        if(args.length < 1)
        {
            System.err.println("Usage: java Main <inputFile>");
            System.exit(-1);
        }

        System.out.println();
        FileInputStream fis = null;
        for (String arg : args)     // supports many files.
        {
            System.out.println("File "+arg);

            fis = new FileInputStream(arg);
            MiniJavaParser parser = new MiniJavaParser(fis);

            Goal root = parser.Goal();
            Visitor1 visit1;

            try{
                /** 1st visitor of HW2 remained to store everything in symbol tables **/
                visit1 = new Visitor1();    // passes filename for error messages
                root.accept(visit1, null);

                while (arg.contains("/"))
                    arg = arg.substring(arg.indexOf("/")+1);
                arg = arg.substring(0, arg.indexOf("."));
                LlvmVisitor visit3 = new LlvmVisitor(arg+".ll", visit1.getAllClasses());
                root.accept(visit3, null);
            }
            catch(Exception ex)
            {   // after catching an exception the program continues to the next file
                System.err.println(ex.getMessage());
                ex.printStackTrace();
                System.out.println();
            }
        }

        try
        {
            if(fis != null)
                fis.close();
        }
        catch(IOException ex)
        {
            System.err.println(ex.getMessage());
        }
    }
}
