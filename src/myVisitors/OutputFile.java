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
        FileWriter writer = new FileWriter(file.getName()); // overwrites file

        for (ClassData aClass : allClasses.getClasses())
        {
            int method_count = aClass.getMethods().size();
            writer.write("@."+aClass.getName()+"_vtable = global ["+method_count+" x i8*] [\n");

            if (aClass.getExtending() != null)
            {
                for (MethodData method : aClass.getExtending().getMethods())
                {
                    writer.write("\ti8* bitcast ("+method.getType()+" (i8*");
                    if (method.getArguments() != null)
                    {
                        for (VariableData argument : method.getArguments())
                        {
                            writer.write(","+argument.getType());
                        }

                    }
                    writer.write(")* @"+aClass.getExtending().getName()+"."+method.getName()+" to i8*)\n");
                }
            }
            for (MethodData method : aClass.getMethods())
            {
                if (method.isOverriding())
                    continue;

                writer.write("\ti8* bitcast ("+method.getType()+" (i8*");
                if (method.getArguments() != null)
                {
                    for (VariableData argument : method.getArguments())
                    {
                        writer.write(","+argument.getType());
                    }

                }
                writer.write(")* @"+aClass.getName()+"."+method.getName()+" to i8*)\n");
            }
            writer.write("]\n\n");
        }
        // main class
        writer.write("@."+allClasses.getMain_class_name()+"_vtable = global [0 x i8*] []\n\n");

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
