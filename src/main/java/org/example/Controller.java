package org.example;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.BufferedReader;
import java.io.File;
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
        // Initialize the script engine manager
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("groovy"); // Use "groovy" or "nashorn" as per your scripting needs

        // Bind variables from the model to the script
        try {
            bindVariablesToScriptEngine(engine);

            // Read and execute the script
            File scriptFile = new File(fname);
            engine.eval(new FileReader(scriptFile));

            // Collect any new variables created by the script
            collectScriptResults(engine);

            return this;
        } catch (Exception e) {
            throw new RuntimeException("Error running script from file: " + fname, e);
        }

    }

    public Controller runScript(String script) {
        // Initialize the script engine manager
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("groovy");

        // Bind variables from the model to the script
        try {
            bindVariablesToScriptEngine(engine);

            // Execute the provided script
            engine.eval(script);

            // Collect any new variables created by the script
            collectScriptResults(engine);

            return this;
        } catch (ScriptException | IllegalAccessException e) {
            throw new RuntimeException("Error running script: " + e.getMessage(), e);
        }
    }

    private void bindVariablesToScriptEngine(ScriptEngine engine) throws IllegalAccessException {
        Field[] fields = model.getClass().getDeclaredFields();

        // Bind @Bind-annotated fields
        for (Field field : fields) {
            if (field.isAnnotationPresent(Bind.class)) {
                field.setAccessible(true);
                engine.put(field.getName(), field.get(model));
            }
        }

        // Bind additional variables like LL
        engine.put("LL", LL);
    }

    private void collectScriptResults(ScriptEngine engine) {
        System.out.println("Collecting results from the script...");
        engine.getBindings(javax.script.ScriptContext.ENGINE_SCOPE).forEach((key, value) -> {
            if (value instanceof double[]) {
                variables.put(key, (double[]) value);
                System.out.println("Collected variable: " + key + " -> " + Arrays.toString((double[]) value));
            }
        });
        // Append the LATA row

    }
    public String getResultsAsTsv() {
        StringBuilder sb = new StringBuilder();

        sb.append("LATA");
        for (int i = 2015; i < 2015 + LL; i++) {
            sb.append("\t").append(i);
        }
        sb.append("\n");

        try {
            Class<?> modelClass = Class.forName(modelName);
            Field[] fields = modelClass.getDeclaredFields();

            // Use reflection to retrieve @Bind annotated fields
            for (Field field : fields) {
                if (field.isAnnotationPresent(Bind.class)) {
                    field.setAccessible(true);
                    String key = field.getName();

                    if (variables.containsKey(key) && !key.equalsIgnoreCase("LATA")) {
                        sb.append(key);

                        double[] values = variables.get(key);
                        for (double value : values) {
                            sb.append("\t").append(value);
                        }
                        sb.append("\n");
                    }
                }
            }

            // Include any dynamically added variables (not present in fields)
            for (String key : variables.keySet()) {
                boolean fieldExists = false;

                // Check if the key corresponds to any @Bind annotated field
                for (Field field : fields) {
                    if (field.isAnnotationPresent(Bind.class) && field.getName().equals(key)) {
                        fieldExists = true;
                        break;
                    }
                }

                // If the variable is not in the fields and it's not "LATA", include it
                if (!fieldExists && !key.equalsIgnoreCase("LATA")) {
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
