package org.example;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

public class View {

    private JFrame frame;
    private JTable resultTable;
    private DefaultTableModel tableModel;
    private JList<String> modelList, dataList;
    private Controller controller;

    public View() {
        frame = new JFrame("Modelling Environment");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLayout(new BorderLayout());

        JPanel selectionPanel = new JPanel(new BorderLayout());
        selectionPanel.setBorder(BorderFactory.createTitledBorder("Select model and data"));

        String[] models = {"Model1"};
        modelList = new JList<>(models);
        modelList.setBorder(BorderFactory.createTitledBorder("Models"));
        selectionPanel.add(new JScrollPane(modelList), BorderLayout.WEST);

        String folderPath = "data";
        File folder = new File(folderPath);
        String[] dataFiles = folder.list((current, name) -> new File(current, name).isFile());
        dataList = new JList<>(dataFiles);
        dataList.setBorder(BorderFactory.createTitledBorder("Data Files"));
        selectionPanel.add(new JScrollPane(dataList), BorderLayout.EAST);

        JButton runButton = new JButton("Run Model");
        runButton.addActionListener(this::onRunModel);
        selectionPanel.add(runButton, BorderLayout.AFTER_LAST_LINE);

        frame.add(selectionPanel, BorderLayout.WEST);

        JPanel resultPanel = new JPanel(new BorderLayout());
        tableModel = new DefaultTableModel();
        resultTable = new JTable(tableModel);
        resultTable.setGridColor(Color.BLACK);
        JScrollPane tableScrollPane = new JScrollPane(resultTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("Results"));

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        JButton runScriptButton = new JButton("Run script from file");
        JButton createScriptButton = new JButton("Create and run ad hoc script");

        runScriptButton.addActionListener(this::onRunScriptFromFile);
        createScriptButton.addActionListener(this::onCreateAndRunAdHocScript);

        buttonPanel.add(runScriptButton);
        buttonPanel.add(createScriptButton);

        resultPanel.add(tableScrollPane, BorderLayout.CENTER);
        resultPanel.add(buttonPanel, BorderLayout.SOUTH);

        frame.add(resultPanel, BorderLayout.CENTER);



        frame.setVisible(true);
    }

    private void onRunModel(ActionEvent e) {
        String selectedModel = modelList.getSelectedValue();
        String selectedData = dataList.getSelectedValue();

        if (selectedModel == null || selectedData == null) {
            JOptionPane.showMessageDialog(frame, "Please select both a model and a data file.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            System.out.println("Running model: " + selectedModel + " with data: " + selectedData);
            controller = new Controller("org.example." + selectedModel);
            controller.readDataFrom("data/" + selectedData).runModel();

            displayTable(controller.getResultsAsTsv());

        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Error reading data file: " + ex.getMessage(), "File Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Error running the model: " + ex.getMessage(), "Model Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onRunScriptFromFile(ActionEvent e) {
        String selectedModel = modelList.getSelectedValue();
        String selectedData = dataList.getSelectedValue();

        if (selectedModel == null || selectedData == null) {
            JOptionPane.showMessageDialog(frame, "Please select both a model and a data file.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser(new File("/Users/tuxqeq/Documents/ITJ/ModelingEnviroment"));
        fileChooser.setDialogTitle("Select Script File");
        int returnValue = fileChooser.showOpenDialog(frame);

        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                controller = new Controller("org.example." + selectedModel);
                controller.readDataFrom("data/" + selectedData).runModel().runScriptFromFile(selectedFile.getAbsolutePath());
                displayTable(controller.getResultsAsTsv());
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame, "Error running script: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onCreateAndRunAdHocScript(ActionEvent e) {
        String selectedModel = modelList.getSelectedValue();
        String selectedData = dataList.getSelectedValue();

        if (selectedModel == null || selectedData == null) {
            JOptionPane.showMessageDialog(frame, "Please select both a model and a data file.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JTextArea scriptArea = new JTextArea(20, 50);
        scriptArea.setLineWrap(true);
        scriptArea.setWrapStyleWord(true);

        int result = JOptionPane.showConfirmDialog(frame, new JScrollPane(scriptArea),
                "Enter Ad Hoc Script", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String script = scriptArea.getText();
            try {
                controller = new Controller("org.example." + selectedModel);
                controller.readDataFrom("data/" + selectedData).runModel().runScript(script);
                displayTable(controller.getResultsAsTsv());
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame, "Error running script: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void displayTable(String resultsTsv) {
        tableModel.setRowCount(0);
        tableModel.setColumnCount(0);

        String[] lines = resultsTsv.split("\n");
        if (lines.length == 0) return;

        String[] headers = lines[0].split("\t");
        tableModel.setColumnIdentifiers(headers);

        NumberFormat numberFormat = NumberFormat.getInstance(Locale.GERMANY);
        numberFormat.setMaximumFractionDigits(3);

        for (int i = 1; i < lines.length; i++) {
            String[] rowData = lines[i].split("\t");
            String[] formattedRowData = new String[rowData.length];

            String rowName = rowData[0];
            formattedRowData[0] = rowName;

            boolean shouldRound = rowName.equals("twKI") || rowName.equals("twKS") || rowName.equals("twINW") ||
                    rowName.equals("twEKS") || rowName.equals("twIMP");

            for (int j = 1; j < rowData.length; j++) {
                try {
                    double value = Double.parseDouble(rowData[j]);

                    if (!shouldRound && value > 2) {
                        value = Math.round(value * 10.0) / 10.0;
                    }

                    String formattedValue = numberFormat.format(value);
                    formattedRowData[j] = formattedValue;
                } catch (NumberFormatException e) {
                    formattedRowData[j] = rowData[j];
                }
            }

            tableModel.addRow(formattedRowData);
        }

        alignTableColumns(resultTable);
    }

    private void alignTableColumns(JTable table) {
        DefaultTableCellRenderer rightAlignRenderer = new DefaultTableCellRenderer();
        rightAlignRenderer.setHorizontalAlignment(SwingConstants.RIGHT);

        for (int i = 1; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(rightAlignRenderer);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(View::new);
    }
}
