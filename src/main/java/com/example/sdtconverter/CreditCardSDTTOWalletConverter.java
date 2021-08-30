package com.example.sdtconverter;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import static com.example.sdtconverter.ExcelUtil.trimQuotesBorder;

// @author : @jpatel10 June.4.2021

public class CreditCardSDTTOWalletConverter {

    private static Logger logger = Logger.getLogger(CreditCardSDTTOWalletConverter.class.getName());
    private static Map<String, Integer> sdtWalletHeadersMap = new HashMap<>(); // to store imporatant columns which required in queries or error codes case

    private static Map<String, Integer> ddRealmWalletHeadersMap = new HashMap<>(); // to store imporatant columns which required in queries or error codes case

    public static void formatExcelToColumns(String inputFileName, String outputFileName) throws IOException {
        ExcelUtil.readAndCreateExcel(inputFileName, outputFileName); // input file to read credit card
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
    public static void createCreditCardWalletIdQuery(String inputFile) throws IOException {
        File customDir = ExcelUtil.getUserHome();

        File excel = new File(inputFile);
        FileInputStream fileInputStream = new FileInputStream(excel);
        XSSFWorkbook sdtToWalletWorkbook = new XSSFWorkbook(fileInputStream);
        XSSFSheet sdtToWalletSheet = sdtToWalletWorkbook.getSheetAt(0);

        //write queries created to a .sql file
        File creditCardWalletIdUpdateSql = new File(customDir + "/creditCard_output_PB_update.sql");
        File creditCardWalletIdRollbackSql = new File(customDir + "/creditCard_output_PB_rollback.sql");

        FileOutputStream creditCardWalletIdUpdateOutputStream = new FileOutputStream(creditCardWalletIdUpdateSql);
        FileOutputStream creditCardWalletIdRollbackOutputStream = new FileOutputStream(creditCardWalletIdRollbackSql);

        // errocodes case : discuss with Chitra, what to do with the failed cases? .... can store these failed alues to a file
        File errorCodeExcel = new File(customDir + "/errorCode-credit-card.xlsx");
        FileOutputStream errorCodeExcelFileOutputStream = new FileOutputStream(errorCodeExcel);
        XSSFWorkbook errorWorkbook = new XSSFWorkbook();
        XSSFSheet errorSheet = errorWorkbook.createSheet();


        // read output file and create update query : read |accountid|cardTokenNumber|walletId|errorCode
        // store the 3 headers indexes in a hashmap
        Row headers = sdtToWalletSheet.getRow(0);

        // creating headers for error sheet
        int erroRowCount = 0;
        Row errorHeaders = errorSheet.createRow(erroRowCount++);

        int cells = headers.getPhysicalNumberOfCells();
        for (int cellIndex = 0; cellIndex < cells; cellIndex++) {

            int errorColumnCount = 0;
            Cell errorAccountId = errorHeaders.createCell(errorColumnCount++);
            errorAccountId.setCellValue("accountId");
            Cell errorCardNumber = errorHeaders.createCell(errorColumnCount++);
            errorCardNumber.setCellValue("cardNumber");
            Cell errorErrorCode = errorHeaders.createCell(errorColumnCount++);
            errorErrorCode.setCellValue("errorCode");

            Cell cell = headers.getCell(cellIndex);
            switch (cell.toString().trim()) {
                case "accountId":
                    sdtWalletHeadersMap.put("accountId", cellIndex);                // keys must match with headers

                    break;
                case "cardNumber":
                    sdtWalletHeadersMap.put("cardNumber", cellIndex);          // keys must match with headers

                    break;
                case "walletId":
                    sdtWalletHeadersMap.put("walletId", cellIndex);                  // keys must match with headers.. no need to store wallet id as it will be always null i case of error
                    break;
                case "errorCode":
                    sdtWalletHeadersMap.put("errorCode", cellIndex);

                    break;
                default:
            }
        }

        // now, iterate the remaining rows
        int rows = sdtToWalletSheet.getPhysicalNumberOfRows() - 1; // excluding headers
        for (int r = 1; r <= rows; r++) {
            Row sdtRow = sdtToWalletSheet.getRow(r);

            if (sdtRow != null) {
                Cell accountId = sdtRow.getCell(sdtWalletHeadersMap.get("accountId")); // read directly the header values by their column index
                Cell cardTokenNumber = sdtRow.getCell(sdtWalletHeadersMap.get("cardNumber")); // read directly the header values by their column index

                // to get wallet id we need to split the walletId string at colon (:)
                Cell walletIdToken = sdtRow.getCell(sdtWalletHeadersMap.get("walletId")); // read directly the header values by their column index

                // check for error codes and avoid any exception in case walletd is null.
                // you can create separate file for failing records i.e. wallet id is null case... or error case.
                Cell errorCode = sdtRow.getCell(sdtWalletHeadersMap.get("errorCode"));
                int errorColumnCount = 0;

                if (Objects.isNull(errorCode)) {
                    continue;
                }
                if (!(errorCode.toString() != null && ExcelUtil.trimQuotesBorder(errorCode.toString()) != "")) { // in normal case : i.e. errorCode field is empty
                    String[] walletIdString = walletIdToken.toString().split(":");
                    String walletId =  walletIdString[1]; // at 1th index will be the wallet id
                    System.out.println();

                    creditCardUpdateQueryBuilder(creditCardWalletIdUpdateOutputStream, accountId, cardTokenNumber, walletId);
                    creditCardRollbackQueryBuilder(creditCardWalletIdRollbackOutputStream, accountId, cardTokenNumber, walletId);
                } else {
                    // order is important here
                    Row errorRow = errorSheet.createRow(erroRowCount++);//errror case
                    // set accounId|cardNumber|errorCode in error sheet
                    Cell errorCellAccountId = errorRow.createCell(errorColumnCount++);
                    errorCellAccountId.setCellValue(ExcelUtil.trimQuotesBorder(accountId.toString()));

                    Cell errorCellCardNumber = errorRow.createCell(errorColumnCount++);
                    errorCellCardNumber.setCellValue(ExcelUtil.trimQuotesBorder(cardTokenNumber.toString()));
                    Cell errorCellErrorCode = errorRow.createCell(errorColumnCount++);
                    errorCellErrorCode.setCellValue(ExcelUtil.trimQuotesBorder(errorCode.toString()));
                }
            }
            }
        errorWorkbook.write(errorCodeExcelFileOutputStream);
        errorCodeExcelFileOutputStream.close();
        creditCardWalletIdUpdateOutputStream.close();
        creditCardWalletIdRollbackOutputStream.close();
        }

    /*
     * Create update query for CCard
     * 4.5. Update credit card walletId query format from converter service output file
     * wiki link for converter : https://wiki.intuit.com/display/qbobilling/SDT+to+Wallet+Conversion
     */
    private static void creditCardUpdateQueryBuilder(FileOutputStream creditCardWalletIdUpdateOutputStream, Cell accountId, Cell cardTokenNumber, String walletId) throws IOException {
          // --update Card Wallet Id ...... discuss with chitra for realmid vs companyid... which one should be in query and why
        // if both are unique, then we should use realmid

        StringBuilder cardWalletQueryBuilder = new StringBuilder();

        // print message to log in db console
        cardWalletQueryBuilder.append("print" + " " + "'" + "realmID is :" + accountId + "'" + ";\n");

        cardWalletQueryBuilder.append( "UPDATE dbo.CompanySecrets SET CardwalletId=" + "'" + walletId + "'" +  " " +
                "WHERE companyId= " + "(" + "SELECT companyId FROM companies WHERE realmID = " + "'" + accountId + "'" + " " + " AND ServiceType IN  ('IOP', 'FS')  AND  PartnerID NOT in ( 26,30 ) AND BillingMethod = 'C'" + ")" + " " +
                "AND CardWalletId IS NULL" + " " +
                "AND CCardNumberToken=" + "'" + cardTokenNumber + "'");

        StringBuilder companyQueryBuilder = new StringBuilder();
        companyQueryBuilder.append("UPDATE dbo.Companies SET Version = Version + 1 WHERE RealmID =" + "'" + accountId + "'" + " AND ServiceType IN  ('IOP', 'FS') AND PartnerID NOT in ( 26,30 ) AND BillingMethod = 'C'");

        logger.info(cardWalletQueryBuilder.toString());
        logger.info(companyQueryBuilder.toString());

        creditCardWalletIdUpdateOutputStream.write(new String(cardWalletQueryBuilder).getBytes(StandardCharsets.UTF_8));
        creditCardWalletIdUpdateOutputStream.write(";\n".getBytes(StandardCharsets.UTF_8));
        creditCardWalletIdUpdateOutputStream.write((new String(companyQueryBuilder).getBytes(StandardCharsets.UTF_8)));
        creditCardWalletIdUpdateOutputStream.write(";\n".getBytes(StandardCharsets.UTF_8));
    }


    private static void creditCardRollbackQueryBuilder(FileOutputStream creditCardWalletIdRollbackOutputStream, Cell accountId, Cell cardTokenNumber, String walletId) throws IOException {
        // creating rollback query for credit card
        StringBuilder cardWalletQueryBuilder = new StringBuilder();

        // print message to log in db console
        cardWalletQueryBuilder.append("print" + " " + "'" + "realmID is : " + accountId + "'" + ";\n") ;

        cardWalletQueryBuilder.append( "UPDATE dbo.CompanySecrets SET CardwalletId = null" + " " +
                "WHERE companyId= " + "(" + "SELECT companyId FROM companies WHERE realmID = " + "'" + accountId + "'" + " " + " AND ServiceType IN  ('IOP', 'FS')  AND  PartnerID NOT in ( 26,30 ) AND BillingMethod = 'C'" + ")" + " " +
                "AND CardWalletId IS NOT NULL" + " " +
                "AND CCardNumberToken=" + "'" + cardTokenNumber + "'") ;

        StringBuilder companyQueryBuilder = new StringBuilder();
        companyQueryBuilder.append("UPDATE dbo.Companies SET Version = Version + 1 WHERE RealmID =" + "'" + accountId + "'" + " AND ServiceType IN  ('IOP', 'FS')   AND  PartnerID NOT in ( 26,30 ) AND BillingMethod = 'C'");

        logger.info(cardWalletQueryBuilder.toString());
        logger.info(companyQueryBuilder.toString());

        creditCardWalletIdRollbackOutputStream.write(new String(cardWalletQueryBuilder).getBytes(StandardCharsets.UTF_8));
        creditCardWalletIdRollbackOutputStream.write(";\n".getBytes(StandardCharsets.UTF_8));
        creditCardWalletIdRollbackOutputStream.write((new String(companyQueryBuilder).getBytes(StandardCharsets.UTF_8)));
        creditCardWalletIdRollbackOutputStream.write(";\n".getBytes(StandardCharsets.UTF_8));
    }


    public static void createDDRemediationCSVFile() throws IOException {
        File customDir = ExcelUtil.getUserHome();
        String inputFile = "ddRemedBefore19Jul2021.xlsx";
        File excel = new File(customDir + "/" + inputFile);

        FileInputStream fileInputStream = new FileInputStream(excel);
        XSSFWorkbook sdtToWalletWorkbook = new XSSFWorkbook(fileInputStream);
        XSSFSheet sdtToWalletSheet = sdtToWalletWorkbook.getSheetAt(0);

        File ddRemediation = new File(customDir + "/ddRemediationOutput" + System.currentTimeMillis() + ".xlsx");
        FileOutputStream ddRemediationFileOutputStream = new FileOutputStream(ddRemediation);
        XSSFWorkbook ddRemediationWorkbook = new XSSFWorkbook();
        XSSFSheet ddRemediationSheet = ddRemediationWorkbook.createSheet();


        // read output file and create update query : read |accountid|cardTokenNumber|walletId|errorCode
        // store the 3 headers indexes in a hashmap
        Row headers = sdtToWalletSheet.getRow(0);

        // creating headers for error sheet
        int erroRowCount = 0;
        Row errorHeaders = ddRemediationSheet.createRow(erroRowCount++);

        int cells = headers.getPhysicalNumberOfCells();
        for (int cellIndex = 0; cellIndex < cells; cellIndex++) {

            int errorColumnCount = 0;
            Cell iIopRealmID = errorHeaders.createCell(errorColumnCount++);
            iIopRealmID.setCellValue("CIOPCLIENTREALMID");
            Cell wWalletID = errorHeaders.createCell(errorColumnCount++);
            wWalletID.setCellValue("WWALLETID");
            Cell iopRealmID = errorHeaders.createCell(errorColumnCount++);
            iopRealmID.setCellValue("IOPCLIENTREALMID");
            Cell walletID = errorHeaders.createCell(errorColumnCount++);
            walletID.setCellValue("WALLETID");

            Cell cell = headers.getCell(cellIndex);
            switch (cell.toString().trim()) {
                case "IOPCLIENTREALMIDWALLETID":
                    ddRealmWalletHeadersMap.put("IOPCLIENTREALMIDWALLETID", cellIndex);                // keys must match with headers

                 default:
            }
        }

        // now, iterate the remaining rows
        int rows = sdtToWalletSheet.getPhysicalNumberOfRows() - 1; // excluding headers
        for (int r = 1; r <= rows; r++) {
            Row sdtRow = sdtToWalletSheet.getRow(r);
            int errorColumnCount = 0;

            if (sdtRow != null) {
                Cell iopRealmWalletID = sdtRow.getCell(ddRealmWalletHeadersMap.get("IOPCLIENTREALMIDWALLETID")); // read directly the header values by their column index

                String[] walletIdString = iopRealmWalletID.toString().split("W");
                String ciopRealmID =  walletIdString[0]; // at 1th index will be the wallet id
                String walletID =  walletIdString[1];
                String iopRealmID = ciopRealmID.substring(1);
                String wWalletId = "W" + walletID;
                System.out.println(iopRealmID +" "+ walletID + " " + ciopRealmID + " " + wWalletId);

                // order is important here
                Row errorRow = ddRemediationSheet.createRow(erroRowCount++);//errror case
                // set accounId|cardNumber|errorCode in error sheet
                Cell ciopRealmIDCell = errorRow.createCell(errorColumnCount++);
                ciopRealmIDCell.setCellValue(ExcelUtil.trimWhiteSpaces(ExcelUtil.trimQuotesBorder(ciopRealmID.toString())));

                Cell wwalletIDCell = errorRow.createCell(errorColumnCount++);
                wwalletIDCell.setCellValue(ExcelUtil.trimWhiteSpaces(ExcelUtil.trimQuotesBorder(wWalletId.toString())));

                //C
                Cell iopRealmIDCell = errorRow.createCell(errorColumnCount++);
                iopRealmIDCell.setCellValue(ExcelUtil.trimWhiteSpaces(ExcelUtil.trimQuotesBorder(iopRealmID.toString())));
                //D
                Cell walletIdCell = errorRow.createCell(errorColumnCount++);
                walletIdCell.setCellValue(ExcelUtil.trimWhiteSpaces(ExcelUtil.trimQuotesBorder(walletID.toString())));

            }
        }
        ddRemediationWorkbook.write(ddRemediationFileOutputStream);
        ddRemediationFileOutputStream.close();
    }


    public static void ddRemediationWallet() throws IOException, Exception {
        createDDRemediationCSVFile();

        // xlsx to csv converter
        File customDir = ExcelUtil.getUserHome();
        String inputFile = "ddRemediationOutput1626962298646.xlsx";
        File excel = new File(customDir + "/" + inputFile);

        ExcelUtil.convertXLXSFileToCSV(excel, 0, customDir);
    }

    public static void WalletConverter() throws IOException {
        // CREDTI CARD
        //CreditCardSDTToWalletConversion();

        // DIRECT DEBIT
        DirectDebitSDTToWalletConversion();


    }

    private static void DirectDebitSDTToWalletConversion() throws IOException {
        DirectDebitSDTTOWalletConverter.formatExcelToColumns("SDT_FILE_PATH_DIRECT_DEBIT", "FORMATTED_OUTPUT_DIRECT_DEBIT"); //TODO
        System.out.println("success!");
        // now read the formatted output file and get values to create the queries
        DirectDebitSDTTOWalletConverter.createDirectDebitWalletIdQuery("FORMATTED_OUTPUT_DIRECT_DEBIT");
        System.out.println("query created");
    }

    private static void CreditCardSDTToWalletConversion() {
        try {

            // read from user home directory : input
            String path = System.getProperty("user.home") + File.separator + "Desktop";
            path += File.separator + "Converter";			//File dir = new File(xmlFilesDirectory);
            //creditCard_output_PB_7jun_41k_1

            // creditCard_output_PB_*.csv to excel
            File latestFileCsv = ExcelUtil.getLastModified(path, "creditCard_output_PB_", ".csv");
            if (latestFileCsv == null) {
                throw new Exception("file not found! Kindly provide the pb output file");
            }

            String getFileNameOnly = ExcelUtil.removeFileExtention(latestFileCsv.getName());
            ExcelUtil.convertCsvToXls(path, latestFileCsv.getAbsolutePath(), getFileNameOnly);

            File latestFileXlsx = ExcelUtil.getLastModified(path, "creditCard_output_PB_", ".xlsx");
            if (latestFileXlsx == null) {
                throw new Exception("file not found! the pb output file: xlsx not created");
            }

            // now read the formatted output file and get values to create the queries
            CreditCardSDTTOWalletConverter.createCreditCardWalletIdQuery(latestFileXlsx.getAbsolutePath());
            System.out.println("query created");


        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
