package org.example;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        Controller ctl = null;
        String datadir = "data";
        try {
            ctl = new Controller("org.example.Model1");
            ctl.readDataFrom(datadir + "/data1.txt").runModel();
            String res = ctl.getResultsAsTsv();
            System.out.println(res);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
