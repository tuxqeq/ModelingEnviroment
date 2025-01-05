package org.example;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        Controller ctl = null;
        String dataDir = "data";
        try {
            ctl = new Controller("org.example.Model1");
            ctl.readDataFrom(dataDir + "data1.txt").runModel();
            String res = ctl.getResultsAsTsv();
            System.out.println(res);
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | IOException e) {
            throw new RuntimeException(e);
        }

    }
}
