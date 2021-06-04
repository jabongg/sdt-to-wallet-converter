package com.example.sdtconverter;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

// @author : @jpatel10 June.4.2021

public class CSVReader {

    private static Logger logger = Logger.getLogger(CSVReader.class.getName());

    public static void readAndCreateExcel() throws IOException {
        File customDir = getUserHome();

        File excel = new File(customDir + "/SDT_TO_wallet_conversion.xlsx");
        FileInputStream fileInputStream = new FileInputStream(excel);
        XSSFWorkbook inputWorkbook = new XSSFWorkbook(fileInputStream);
        XSSFSheet inputSheet = inputWorkbook.getSheetAt(0);

        // formatted excel : output
        XSSFWorkbook outputWorkbook = new XSSFWorkbook();
        FileOutputStream fileOutputStream = new FileOutputStream(customDir + "/formatted" + System.currentTimeMillis() + ".xlsx");
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

    private static File getUserHome() {
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

    /**
    4.5. Update credit card walletId query format from converter service output file
    --update Card Wallet Id
    UPDATE dbo.CompanySecrets SET CardwalletId = '<<param1>>' , hk_modified = GETDATE()
    WHERE Companyid = <<param2>>
    AND CardWalletId IS NULL
    AND CCardNumberToken = '<<param3>>';

    --update company version
    UPDATE dbo.Companies SET Version = Version + 1, hk_modified = GETDATE() WHERE CompanyId = <<param2>>;

    -- <<param1>> : card walletId from converter service output file
    -- <<param2>> : company id (accountid) referenced in the input file query  // also present in output file
    -- <<param3>> : card token number (cardNumber) referenced in the input file query // also present in output file

    so query can be created using output file only
     */
    public static void createCreditCardWalletIdQuery() throws IOException {
        File customDir = getUserHome();

        File excel = new File(customDir + "/formatted.xlsx");
        FileInputStream fileInputStream = new FileInputStream(excel);
        XSSFWorkbook sdtToWalletWorkbook = new XSSFWorkbook(fileInputStream);
        XSSFSheet sdtToWalletSheet = sdtToWalletWorkbook.getSheetAt(0);

        //write queries created to a .sql file
        File creditCardWalletIdSql = new File(customDir + "/credit-card-wallet-id.sql");

        // read output file and create update query : read |accountid|cardTokenNumber|walletId|
        // store the 3 headers indexes in a hashmap

        Map<String, Integer> sdtWalletHeadersMap = new HashMap<>();

        Row headers = sdtToWalletSheet.getRow(0);
        int cells = headers.getPhysicalNumberOfCells();
        for (int cellIndex = 0; cellIndex < cells; cellIndex++) {
            Cell cell = headers.getCell(cellIndex);
            switch (cell.toString().trim()) {
                case "accountId":
                    sdtWalletHeadersMap.put("accountId", cellIndex);                // keys must match with headers
                    break;
                case "cardNumber":
                    sdtWalletHeadersMap.put("cardNumber", cellIndex);          // keys must match with headers
                    break;
                case "walletId":
                    sdtWalletHeadersMap.put("walletId", cellIndex);                  // keys must match with headers
                    break;
                default:
            }
        }

        // now, iterate the remaining rows
        int rows = sdtToWalletSheet.getPhysicalNumberOfRows() - 1; // excluding headers
        for (int r = 1; r <= rows; r++) {
            Row sdtRow = sdtToWalletSheet.getRow(r);
            if (sdtRow != null) {
                int sdtCells = sdtRow.getPhysicalNumberOfCells();
                Cell accountId = sdtRow.getCell(sdtWalletHeadersMap.get("accountId")); // read directly the header values by their column index
                Cell cardTokenNumber = sdtRow.getCell(sdtWalletHeadersMap.get("cardNumber")); // read directly the header values by their column index

                // to get wallet id we need to split the walletId string at colon (:)
                Cell walletIdToken = sdtRow.getCell(sdtWalletHeadersMap.get("walletId")); // read directly the header values by their column index

                String[] walletIdString = walletIdToken.toString().split(":");
                String walletId =  walletIdString[1]; // at 1th index will be the wallet id
                System.out.println();


                /*
                 * Create update query for CCard
                 * 4.5. Update credit card walletId query format from converter service output file
                 * wiki link for converter : https://wiki.intuit.com/display/qbobilling/SDT+to+Wallet+Conversion
                 */
                // --update Card Wallet Id ...... discuss with chitra for realmid vs companyid... which one should be in query and why
                // if both are unique, then we should use realmid

                StringBuilder cardWalletQueryBuilder = new StringBuilder();

                /* cardWalletQueryBuilder.append( "UPDATE dbo.CompanySecrets SET CardwalletId=" + walletId + ", hk_modified = GETDATE()" + " " +
                        "WHERE RealmID=" + accountId + " " +
                        "AND CardWalletId IS NULL" + " " +
                        "AND CCardNumberToken=" + cardTokenNumber);*/

                cardWalletQueryBuilder.append( "UPDATE dbo.CompanySecrets SET CardwalletId=" + walletId + ", hk_modified = GETDATE()" + " " +
                        "WHERE RealmID=" + accountId + " " +
                        "AND CardWalletId IS NULL" + " " +
                "AND CCardNumberToken=" + cardTokenNumber);

                /*
                 * update company version
                 * Note: accountId is RealmId of a company but for companyid,
                 * we have to fetch company id corresponding to the realmid i.e. accountId here.
                 * why companyid if we can use realmid: since both are unique and realmid is more useful
                 *
                 * so Tauqeer's query need to updated, otherwise there will be un-necessary burden.
                 * discuss this with chitra and subho
                 */
                StringBuilder companyQueryBuilder = new StringBuilder();
                //companyQueryBuilder.append("UPDATE dbo.Companies SET Version = Version + 1, hk_modified = GETDATE() WHERE CompanyId = " + accountId);
                companyQueryBuilder.append("UPDATE dbo.Companies SET Version = Version + 1, hk_modified = GETDATE() WHERE RealmID =" + accountId);

                logger.info(cardWalletQueryBuilder.toString());
                logger.info(companyQueryBuilder.toString());



            }
            }
        }
}
