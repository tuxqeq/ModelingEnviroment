package org.example;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

public class Controller {
    private Object model;
    private String modelName;
    private int LL; // Number of years for calculations
    private Map<String, double[]> variables = new HashMap<>();

    public Controller(String modelName) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        this.modelName = modelName;
        Class<?> modelClass = Class.forName(modelName);
        model = modelClass.newInstance();
    }

    public Controller readDataFrom(String fname) throws IOException, IllegalAccessException {
        try (BufferedReader br = new BufferedReader(new FileReader(fname))) {
            String header = br.readLine(); // Read the header row
            String[] tokens = header.split("\\s+");
            if (!tokens[0].equals("LATA")) {
                throw new IllegalArgumentException("File must start with 'LATA'");
            }

            LL = tokens.length - 1; // Calculate the number of years
            double[] years = new double[LL];
            for (int i = 0; i < LL; i++) {
                years[i] = Double.parseDouble(tokens[i + 1]);
            }
            variables.put("LATA", years);

            // Parse the remaining lines
            String line;
            while ((line = br.readLine()) != null) {
                tokens = line.split("\\s+");
                String varName = tokens[0];
                double[] values = new double[LL];
                Arrays.fill(values, Double.NaN); // Default values as NaN

                for (int i = 1; i < tokens.length; i++) {
                    values[i - 1] = Double.parseDouble(tokens[i]);
                }

                // Fill missing values with the last provided value
                for (int i = 1; i < LL; i++) {
                    if (Double.isNaN(values[i])) {
                        values[i] = values[i - 1];
                    }
                }

                variables.put(varName, values);
            }
        }

        bindVariablesToModel();
        return this;
    }

    private void bindVariablesToModel() throws IllegalAccessException {
        Field[] fields = model.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(Bind.class) && variables.containsKey(field.getName())) {
                field.setAccessible(true);
                field.set(model, variables.get(field.getName()));
            }
        }
    }

    public Controller runModel() throws IllegalAccessException {
        Field[] fields = model.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(Bind.class)) {
                field.setAccessible(true);
                if (field.getName().equals("LL")) {
                    field.set(model, LL);
                } else if (variables.containsKey(field.getName())) {
                    field.set(model, variables.get(field.getName()));
                }
            }
        }

        try {
            System.out.println("trying to run the model method run...");
            model.getClass().getMethod("run").invoke(model);
            System.out.println("Model run successfully");

            // After running the model, fetch updated variable values
            for (Field field : fields) {
                if (field.isAnnotationPresent(Bind.class)) {
                    field.setAccessible(true);
                    Object fieldValue = field.get(model); // Fetch the value of the field
                    if (fieldValue instanceof double[]) {
                        variables.put(field.getName(), (double[]) fieldValue);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error running the model", e);
        }

        return this;
    }

    public Controller runScriptFromFile(String fname) {

        return null;
    }

    public Controller runScript(String script) {

        return null;
    }

    public String getResultsAsTsv() {
        StringBuilder sb = new StringBuilder();

        // Append the LATA row
        sb.append("LATA");
        for (int i = 2015; i < 2015 + LL; i++) {
            sb.append("\t").append(i);
        }
        sb.append("\n");

        // Use reflection to get the fields in order they were declarated
        try {
            Class<?> modelClass = Class.forName(modelName);
            Field[] fields = modelClass.getDeclaredFields();

            for (Field field : fields) {
                if (field.isAnnotationPresent(Bind.class) && variables.containsKey(field.getName())) {
                    String key = field.getName();
                    sb.append(key);

                    double[] values = variables.get(key);
                    for (double value : values) {
                        sb.append("\t").append(value);
                    }
                    sb.append("\n");
                }
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Model class not found: " + modelName, e);
        }

        return sb.toString();
    }
}
