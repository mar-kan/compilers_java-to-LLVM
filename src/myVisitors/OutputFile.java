package myVisitors;

import symbols.AllClasses;
import symbols.ClassData;
import symbols.MethodData;
import symbols.VariableData;

import java.io.*;

public class OutputFile {

    File file;


    public OutputFile(String filename, AllClasses allClasses) throws IOException
    {
        this.file = new File(filename);
        this.writeVtables(allClasses);
        this.writeBoilerplate();
    }

    public void writeString(String str) throws IOException
    {
        FileWriter writer = new FileWriter(file.getName(), true);   // appends to file

        writer.write(str);
        writer.flush();
        writer.close();
    }

    public void writeVtables(AllClasses allClasses) throws IOException
    {
        FileWriter writer = new FileWriter(file.getName());     // overwrites file
        boolean flag;

        for (int i=allClasses.getClasses().size()-1; i>=0; i--) // printing classes in reverse
        {
            flag = false;
            ClassData aClass = allClasses.getClasses().get(i);
            int method_count = aClass.getMethodSizeWithExtending();

            // writes class vtable
            writer.write("@."+aClass.getName()+"_vtable = global ["+method_count+" x i8*] [");

            // writes all its methods
            for (MethodData method : aClass.getMethods())
            {
                if (flag)
                    writer.write(",\n");
                else
                    writer.write('\n');

                String type = method.getType();
                if (!type.equals("i32") && !type.equals("i32*") && !type.equals("i1"))
                    type = "i8*";

                writer.write("\ti8* bitcast ("+type+" (i8*");
                flag = true;

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

            // writes every method of the extending that aClass isn't overridden by the subclass
            if (aClass.getExtending() != null)
            {
                for (MethodData method : aClass.getExtending().getMethods())
                {
                    if (method.isOverridden())
                        continue;
                    if (flag)
                        writer.write(",\n");
                    else
                        writer.write('\n');

                    String type = method.getType();
                    if (!type.equals("i32") && !type.equals("i32*") && !type.equals("i1"))
                        type = "i8*";

                    writer.write("\ti8* bitcast ("+type+" (i8*");

                    flag = true;
                    if (method.getArguments() != null)
                    {
                        for (VariableData argument : method.getArguments())
                        {
                            type = argument.getType();
                            if (!type.equals("i32") && !type.equals("i32*") && !type.equals("i1"))
                                type = "i8*";
                            writer.write(","+type);
                        }
                    }
                    writer.write(")* @"+aClass.getExtending().getName()+"."+method.getName()+" to i8*)\n");
                }
            }
            writer.write("]\n\n");
        }

        // main class
        writer.write("@."+allClasses.getMain_class_name()+"_vtable = global ["+allClasses.getMainClass().getMethods().size()+" x i8*] []\n\n");

        // main class' methods
        flag = false;
        for (MethodData method : allClasses.getMainClass().getMethods())
        {
            if (flag)
                writer.write(",\n");
            else
                writer.write('\n');

            String type = method.getType();
            if (!type.equals("i32") && !type.equals("i32*") && !type.equals("i1"))
                type = "i8*";

            writer.write("\ti8* bitcast ("+type+" (i8*");

            flag = true;

            if (method.getArguments() != null)
            {
                for (VariableData argument : method.getArguments())
                {
                    type = argument.getType();
                    if (!type.equals("i32") && !type.equals("i32*") && !type.equals("i1"))
                        type = "i8*";
                    writer.write(","+type);
                }

            }
            writer.write(")* @"+allClasses.getMain_class_name()+"."+method.getName()+" to i8*)");
        }
        writer.flush();
        writer.close();
    }

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
}
