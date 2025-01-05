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
    private int LL;
    private Map<String, double[]> variables = new HashMap<>();

    public Controller(String modelName) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        this.modelName = modelName;
        Class<?> modelClass = Class.forName(modelName);
        model = modelClass.newInstance();
    }

    public Controller readDataFrom(String fname) throws IOException, IllegalAccessException {
        try (BufferedReader br = new BufferedReader(new FileReader(fname))) {
            String header = br.readLine();
            String[] tokens = header.split("\\s+");
            if (!tokens[0].equals("LATA")) {
                throw new IllegalArgumentException("File must start with 'LATA'");
            }

            LL = tokens.length - 1;
            double[] years = new double[LL];
            for (int i = 0; i < LL; i++) {
                years[i] = Double.parseDouble(tokens[i + 1]);
            }
            variables.put("LATA", years);

            String line;
            while ((line = br.readLine()) != null) {
                tokens = line.split("\\s+");
                String varName = tokens[0];
                double[] values = new double[LL];
                Arrays.fill(values, Double.NaN);

                for (int i = 1; i < tokens.length; i++) {
                    values[i - 1] = Double.parseDouble(tokens[i]);
                }

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
            model.getClass().getMethod("run").invoke(model);

            for (Field field : fields) {
                if (field.isAnnotationPresent(Bind.class)) {
                    field.setAccessible(true);
                    Object fieldValue = field.get(model);
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

    public void runScriptFromFile(String fname) {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("groovy");

        try {
            bindVariablesToScriptEngine(engine);

            File scriptFile = new File(fname);
            engine.eval(new FileReader(scriptFile));

            collectScriptResults(engine);

        } catch (Exception e) {
            throw new RuntimeException("Error running script from file: " + fname, e);
        }

    }

    public void runScript(String script) {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("groovy");

        try {
            bindVariablesToScriptEngine(engine);

            engine.eval(script);

            collectScriptResults(engine);

        } catch (ScriptException | IllegalAccessException e) {
            throw new RuntimeException("Error running script: " + e.getMessage(), e);
        }
    }

    private void bindVariablesToScriptEngine(ScriptEngine engine) throws IllegalAccessException {
        Field[] fields = model.getClass().getDeclaredFields();

        for (Field field : fields) {
            if (field.isAnnotationPresent(Bind.class)) {
                field.setAccessible(true);
                engine.put(field.getName(), field.get(model));
            }
        }

        engine.put("LL", LL);
    }

    private void collectScriptResults(ScriptEngine engine) {
        engine.getBindings(javax.script.ScriptContext.ENGINE_SCOPE).forEach((key, value) -> {
            if (value instanceof double[]) {
                variables.put(key, (double[]) value);
            }
        });

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

            for (String key : variables.keySet()) {
                boolean fieldExists = false;

                for (Field field : fields) {
                    if (field.isAnnotationPresent(Bind.class) && field.getName().equals(key)) {
                        fieldExists = true;
                        break;
                    }
                }

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
