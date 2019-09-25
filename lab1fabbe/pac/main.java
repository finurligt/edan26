package pac;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

public class main {
    public static void main(String[] args) throws FileNotFoundException {
        PrintStream fileStream = new PrintStream(new File("dataflow.txt"));
        System.setOut(fileStream);

        Dataflow dataflow = new Dataflow();
        String[] dataflowArgs = {"10000","1000","4", "100", "4", "1"};

        dataflow.myMain(dataflowArgs);



        PrintStream fileStream2 = new PrintStream(new File("paradataflow.txt"));
        System.setOut(fileStream2);

        ParaDataflow paraDataflow = new ParaDataflow();

        paraDataflow.myMain(dataflowArgs);

    }
}
