package com.example.sdtconverter;

import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.formula.functions.Rows;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;

public class CSVReader {

    public static void readCsv() throws IOException {
        File excel = new File("/Users/jpatel10/Desktop/Converter/SDT_TO_wallet_conversion.xlsx");
        FileInputStream fileInputStream = new FileInputStream(excel);
        XSSFWorkbook workbook = new XSSFWorkbook(fileInputStream);
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
        fileInputStream.close();
    }
}
