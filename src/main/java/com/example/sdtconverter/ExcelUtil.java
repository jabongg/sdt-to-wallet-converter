package com.example.sdtconverter;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;

import java.util.logging.Logger;

public class ExcelUtil {
    private static Logger logger = Logger.getLogger(CreditCardSDTTOWalletConverter.class.getName());

     public static void readAndCreateExcel(String inputFileName, String outputFileName) throws IOException {
        File customDir = getUserHome();

        File excel = new File(inputFileName);
        FileInputStream fileInputStream = new FileInputStream(excel);
        XSSFWorkbook inputWorkbook = new XSSFWorkbook(fileInputStream);
        XSSFSheet inputSheet = inputWorkbook.getSheetAt(0);

        // formatted excel : output
        XSSFWorkbook outputWorkbook = new XSSFWorkbook();
        FileOutputStream fileOutputStream = new FileOutputStream(customDir + "/" +outputFileName);
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
            while (inputCellIterator.hasNext()) {
                Cell inputCell = inputCellIterator.next();
                logger.info(inputCell.toString());

                /*
                 * split logic goes here
                 * split cell by pipe separator '|'
                 */
                int outputCellCount = 0;

                String inputStr = inputCell.toString();
                String[] inputDetails = inputStr.split("\\|");
                for (String inputDetailCell : inputDetails) {
                    Cell outputCell = outputRow.createCell(outputCellCount++);
                    outputCell.setCellValue(trimQuotesBorder(inputDetailCell));
                }
            }
            System.out.println();
        }

        outputWorkbook.write(fileOutputStream);
        outputWorkbook.close();
        inputWorkbook.close();
        fileInputStream.close();
    }

    public static File getUserHome() {
        // read from user home directory : input
        String path = System.getProperty("user.home") + File.separator + "Desktop";
        path += File.separator + "Converter";
        File customDir = new File(path);

        if (customDir.exists()) {
            logger.info(customDir + " already exists");
        } else if (customDir.mkdirs()) {
            logger.info(customDir + " was created");
        } else {
            logger.info(customDir + " was not created");
        }
        return customDir;
    }

    public static String trimQuotes(String str) {
        return str.replace("\"", "");
    }

    // can handle names like "Tonny "Jerry" Johns" -> Tonny "Jerry" Johns
    public static String trimQuotesBorder(String str) {
        return str.replaceAll("^(['\"])(.*)\\1$", "$2");
    }

    public static File getLastModified(String directoryFilePath)
    {
        File directory = new File(directoryFilePath);
        File[] files = directory.listFiles(File::isFile);
        long lastModifiedTime = Long.MIN_VALUE;
        File chosenFile = null;

        if (files != null)
        {
            for (File file : files)
            {
                if (file.lastModified() > lastModifiedTime)
                {
                    if (file.getName().startsWith("creditCard_output_PB_")) {
                    chosenFile = file;
                    lastModifiedTime = file.lastModified();
                }
                }
            }
        }

        return chosenFile;
    }
}
