package com.example.sdtconverter;

import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.formula.functions.Rows;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.Iterator;

public class CSVReader {

    public static void readCsv() throws IOException {
        // excel : input
        File excel = new File("/Users/jpatel10/Desktop/Converter/SDT_TO_wallet_conversion.xlsx");
        FileInputStream fileInputStream = new FileInputStream(excel);
        XSSFWorkbook inputWorkbook = new XSSFWorkbook(fileInputStream);
        XSSFSheet inputSheet = inputWorkbook.getSheetAt(0);

        // formatted excel : output
        XSSFWorkbook outputWorkbook = new XSSFWorkbook();
        FileOutputStream fileOutputStream = new FileOutputStream("/Users/jpatel10/Desktop/Converter/formatted" + System.currentTimeMillis()+ ".xlsx");
        XSSFSheet outputSheet = outputWorkbook.createSheet();

        int rowCount = 0;
        // iterate in rows
        Iterator<Row> rowsIterator = inputSheet.iterator();
        while (rowsIterator.hasNext()) {
            //get the row
            Row inputRow = rowsIterator.next();
            Row outputRow = outputSheet.createRow(rowCount++);
            // iterate cells from the current row
            Iterator<Cell> inputCellIterator = inputRow.cellIterator();
            Iterator<Cell> outputCellIterator = outputRow.cellIterator();
            while (inputCellIterator.hasNext()) {
                int cellCount = 0;
                Cell inputCell = inputCellIterator.next();
                System.out.println(inputCell.toString());

                //logic goes here
                Cell outputCell = outputRow.createCell(cellCount++);
                outputCell.setCellValue(inputCell.toString());
                //System.out.println(outputCell + " ");
            }
            System.out.println();
        }

        outputWorkbook.write(fileOutputStream);
        outputWorkbook.close();
        inputWorkbook.close();
        fileInputStream.close();
    }

    public  static void createFormattedCsv(Workbook workbook) throws IOException {

        Workbook formattedWorkbook = new XSSFWorkbook();
        Sheet formattedSheet = formattedWorkbook.createSheet();


        FileOutputStream fileOutputStream = new FileOutputStream("/Users/jpatel10/Desktop/Converter/formatted.xlsx");
        formattedWorkbook.write(fileOutputStream);
        formattedWorkbook.close();
        workbook.close();
    }


    public static void writeCsv() throws IOException {
        File excel = new File("/Users/jpatel10/Desktop/Converter/formatted.xlsx");
        FileOutputStream fileOutputStream = new FileOutputStream(excel);
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.getSheetAt(0);

        // iterate in rows
        Iterator<Row> rowsIterator = sheet.iterator();
        while (rowsIterator.hasNext()) {
            //get the row
            Row row = rowsIterator.next();

            // iterate cells from the current row
            Iterator<Cell> cellIterator = row.cellIterator();
            while (cellIterator.hasNext()) {
                Cell cell = cellIterator.next();
                System.out.println(cell.toString() + " ;");
            }
            System.out.println();
        }

        workbook.close();
        //fileInputStream.close();
    }
}
