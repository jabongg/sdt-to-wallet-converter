package com.example.sdtconverter;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.util.ObjectUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

// @author : @jpatel10 June.6.2021

public class DirectDebitSDTTOWalletConverter implements WalletConverter {

    private static Logger logger = Logger.getLogger(DirectDebitSDTTOWalletConverter.class.getName());
    private static Map<String, Integer> sdtWalletHeadersMap = new HashMap<>(); // to store imporatant columns which required in queries or error codes case
    private static Map<String, Integer> sdtWalletDetokenizeHeadersMap = new HashMap<>();
    private static Map<String, DetokenizePOJO> detokenizePOJOMap = new HashMap<>(); //using map as cache which will later be looked upon

    public static void formatExcelToColumns(String inputFileName, String outputFileName) throws IOException {
        ExcelUtil.readAndCreateExcel(inputFileName, outputFileName); // input file to read credit card
    }

    /**
     UPDATE dbo.BillingBankAccountInfo
     SET BankWalletId = '<<param1>>', hk_modified = GETDATE()
     WHERE CompanyId = <<param2>>
     AND BankWalletId IS NULL
     AND AccountNumber = '<<param3>>'
     AND RoutingNumber = '<<param4>>';

     --update company version
     UPDATE dbo.Companies
     SET Version = Version + 1, hk_modified = GETDATE()
     WHERE CompanyId = <<param2>>;

     -- <<param1>> : bank walletId from converter service output file
     -- <<param2>> : company Id from converter service output file
     -- <<param3>> : last 4 digit of  bank account number referenced in the input file query
     -- <<param4>> : last 4 digit of  bank routing number referenced in the input file query ---- discuss with Chitra : which value?
     * @param inputFile
     */
    public static void createDirectDebitWalletIdQuery(String inputFile) throws IOException {
        File customDir = ExcelUtil.getUserHome();

        File excel = new File(inputFile);
        FileInputStream fileInputStream = new FileInputStream(excel);
        XSSFWorkbook sdtDirectDebitToWalletWorkbook = new XSSFWorkbook(fileInputStream);
        XSSFSheet sdtDirectDebitToWalletSheet = sdtDirectDebitToWalletWorkbook.getSheetAt(0);

        //write queries created to a .sql file
        File directDebitWalletIdUpdateSql = new File(customDir + "/direct-debit-wallet-id-update.sql");
        File directDebitWalletIdRollbackSql = new File(customDir + "/direct-debit-wallet-id-rollback.sql");

        FileOutputStream directDebitWalletIdUpdateOutputStream = new FileOutputStream(directDebitWalletIdUpdateSql);
        FileOutputStream directDebitWalletIdRollbackOutputStream = new FileOutputStream(directDebitWalletIdRollbackSql);

        // errocodes case : discuss with Chitra, what to do with the failed cases? .... can store these failed alues to a file
        File errorCodeExcel = new File(customDir + "/errorCode-direct-debit.xlsx");
        FileOutputStream errorCodeExcelFileOutputStream = new FileOutputStream(errorCodeExcel);
        XSSFWorkbook errorWorkbook = new XSSFWorkbook();
        XSSFSheet errorSheet = errorWorkbook.createSheet();


        // read output file and create update query : read |accountid|cardTokenNumber|walletId|errorCode
        // store the 3 headers indexes in a hashmap
        Row headers = sdtDirectDebitToWalletSheet.getRow(0);

        // creating headers for error sheet
        int erroRowCount = 0;
        Row errorHeaders = errorSheet.createRow(erroRowCount++);

        int cells = headers.getPhysicalNumberOfCells();
        int errorColCount = 0;
        Cell errorAccountId = errorHeaders.createCell(errorColCount++);
        errorAccountId.setCellValue("accountId");
        Cell errorAccountNumber = errorHeaders.createCell(errorColCount++);
        errorAccountNumber.setCellValue("accountNumber");
        Cell errorBankCode = errorHeaders.createCell(errorColCount++);
        errorBankCode.setCellValue("bankCode");
        Cell errorErrorCode = errorHeaders.createCell(errorColCount++);
        errorErrorCode.setCellValue("errorCode");

        for (int cellIndex = 0; cellIndex < cells; cellIndex++) {



            Cell cell = headers.getCell(cellIndex);
            switch (cell.toString().trim()) {
                case "accountId":
                    sdtWalletHeadersMap.put("accountId", cellIndex);                // keys must match with headers

                    break;
                case "accountNumber":
                    sdtWalletHeadersMap.put("accountNumber", cellIndex);          // keys must match with headers

                    break;
                case "walletId":
                    sdtWalletHeadersMap.put("walletId", cellIndex);                  // keys must match with headers.. no need to store wallet id as it will be always null in case of error
                    break;
                case "bankCode": // for routing number, which value to be mapped from input file  : is it bankCode?
                    sdtWalletHeadersMap.put("bankCode", cellIndex);                  // keys must match with headers.. no need to store wallet id as it will be always null in case of error
                    break;
                case "errorCode":
                    sdtWalletHeadersMap.put("errorCode", cellIndex);

                    break;
                default:
            }
        }

        // now, iterate the remaining rows
        int rows = sdtDirectDebitToWalletSheet.getPhysicalNumberOfRows() - 1; // excluding headers
        for (int r = 1; r <= rows; r++) {
            Row sdtRow = sdtDirectDebitToWalletSheet.getRow(r);

            if (sdtRow != null) {
                Cell accountId = sdtRow.getCell(sdtWalletHeadersMap.get("accountId")); // read directly the header values by their column index
                Cell accountNumber = sdtRow.getCell(sdtWalletHeadersMap.get("accountNumber")); // read directly the header values by their column index

                // to get wallet id we need to split the walletId string at colon (:)
                Cell walletIdToken = sdtRow.getCell(sdtWalletHeadersMap.get("walletId")); // read directly the header values by their column index
                Cell bankCode = sdtRow.getCell(sdtWalletHeadersMap.get("bankCode"));

                // check for error codes and avoid any exception in case walletd is null.
                // you can create separate file for failing records i.e. wallet id is null case... or error case.
                Cell errorCode = sdtRow.getCell(sdtWalletHeadersMap.get("errorCode"));
                int errorColumnCount = 0;
               if (Objects.isNull(errorCode)) {
                   continue;
               }
                    if (!(errorCode.toString() != null && ExcelUtil.trimQuotesBorder(errorCode.toString()) != "")) { // in normal case : i.e. errorCode field is empty
                        String[] walletIdString = walletIdToken.toString().split(":");
                        String walletId = walletIdString[1]; // at 1th index will be the wallet id
                        System.out.println();

                        directDebitUpdateQueryBuilder(directDebitWalletIdUpdateOutputStream, accountId, accountNumber, walletId, bankCode);
                        directDebitRollbackQueryBuilder(directDebitWalletIdRollbackOutputStream, accountId, accountNumber, walletId, bankCode);
                    } else {
                        // order is important here
                        Row errorRow = errorSheet.createRow(erroRowCount++);//errror case
                        // set accounId|cardNumber|errorCode in error sheet
                        Cell errorCellAccountId = errorRow.createCell(errorColumnCount++);
                        errorCellAccountId.setCellValue(ExcelUtil.trimQuotesBorder(accountId.toString()));
                        Cell errorCellAccountNumber = errorRow.createCell(errorColumnCount++);
                        errorCellAccountNumber.setCellValue(ExcelUtil.trimQuotesBorder(accountNumber.toString()));

                        Cell errorCellBankCode = errorRow.createCell(errorColumnCount++);
                        errorCellBankCode.setCellValue(ExcelUtil.trimQuotesBorder(errorCellBankCode.toString()));
                        Cell errorCellErrorCode = errorRow.createCell(errorColumnCount++);
                        errorCellErrorCode.setCellValue(ExcelUtil.trimQuotesBorder(errorCode.toString()));
                    }
            //    }
            }
        }
        errorWorkbook.write(errorCodeExcelFileOutputStream);
        errorCodeExcelFileOutputStream.close();
        directDebitWalletIdUpdateOutputStream.close();
        directDebitWalletIdRollbackOutputStream.close();
    }

    /*
     * Create update query for CCard
     * 4.5. Update credit card walletId query format from converter service output file
     * wiki link for converter : https://wiki.intuit.com/display/qbobilling/SDT+to+Wallet+Conversion
     */
    private static void directDebitUpdateQueryBuilder(FileOutputStream directDebitWalletIdUpdateOutputStream, Cell accountId, Cell accountNumber, String walletId, Cell bankCode) throws IOException {
        // --update Card Wallet Id ...... discuss with chitra for realmid vs companyid... which one should be in query and why
        // if both are unique, then we should use realmid

        String accountNumberLastFour = getLastFourSubstring(accountNumber.toString()); // get last 4 digits of account number
        String bankCodeLastFour = getLastFourSubstring(bankCode.toString());

        StringBuilder directDebitWalletQueryBuilder = new StringBuilder();

        directDebitWalletQueryBuilder.append("UPDATE dbo.BillingBankAccountInfo SET BankWalletId =" +  "'"+ walletId+ "'" + ", + hk_modified = GETDATE()"+ " " +
                "WHERE CompanyId =" + "'"  + accountId.toString()+ "'" + " " +
                "AND BankWalletId IS NULL" + " " +
                "AND AccountNumber =" +  "'" + accountNumberLastFour + "'" + " " +
                "AND RoutingNumber =" + "'" + bankCodeLastFour + "'");

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
        companyQueryBuilder.append("UPDATE dbo.Companies SET Version = Version + 1, hk_modified = GETDATE() WHERE RealmID =" + "'" + accountId + "'");

        logger.info(directDebitWalletQueryBuilder.toString());
        logger.info(companyQueryBuilder.toString());

        directDebitWalletIdUpdateOutputStream.write(new String(directDebitWalletQueryBuilder).getBytes(StandardCharsets.UTF_8));
        directDebitWalletIdUpdateOutputStream.write(";\n".getBytes(StandardCharsets.UTF_8));
        directDebitWalletIdUpdateOutputStream.write((new String(companyQueryBuilder).getBytes(StandardCharsets.UTF_8)));
        directDebitWalletIdUpdateOutputStream.write(";\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String getLastFourSubstring(String str) {
        return str.substring(str.length() - 4);
    }


    private static void directDebitRollbackQueryBuilder(FileOutputStream directDebitWalletIdRollbackOutputStream, Cell accountId, Cell accountNumber, String walletId, Cell bankCode) throws IOException {
        // creating rollback query for credit card
        StringBuilder directDebitWalletQueryBuilder = new StringBuilder();

        String accountNumberLastFour = getLastFourSubstring(accountNumber.toString()); // get last 4 digits of account number
        String bankCodeLastFour = getLastFourSubstring(bankCode.toString());

        /*
            UPDATE dbo.BillingBankAccountInfo
            SET BankWalletId = null , hk_modified = GETDATE()
            WHERE CompanyId = <<param1>>
            AND AccountNumber = '<<param2>>'
            AND RoutingNumber = '<<param3>>';

            --update company version
            UPDATE dbo.Companies
            SET Version = Version + 1, hk_modified = GETDATE()
            WHERE CompanyId = <<param1>>;

            -- <<param1>> : company Id from converter service output file
            -- <<param2>> : last 4 digit of  bank account number referenced in the input file query
            -- <<param3>> : last 4 digit of  bank routing number referenced in the input file query
            */
        directDebitWalletQueryBuilder.append( " UPDATE dbo.BillingBankAccountInfo" +
                "SET BankWalletId = null, hk_modified = GETDATE()" + " " +
                "WHERE RealmID=" + "'" + accountId  + "'" + " " +
                "AND AccountNumber =" + "'" +  accountNumberLastFour + "'" + " " +
                "AND RoutingNumber =" +  "'" +  bankCodeLastFour + "'"); // routing number related and company id related changes to be performed

        StringBuilder companyQueryBuilder = new StringBuilder();
        companyQueryBuilder.append("UPDATE dbo.Companies SET Version = Version + 1, hk_modified = GETDATE() WHERE RealmID =" + "'" + accountId + "'");

        logger.info(directDebitWalletQueryBuilder.toString());
        logger.info(companyQueryBuilder.toString());

        directDebitWalletIdRollbackOutputStream.write(new String(directDebitWalletQueryBuilder).getBytes(StandardCharsets.UTF_8));
        directDebitWalletIdRollbackOutputStream.write(";\n".getBytes(StandardCharsets.UTF_8));
        directDebitWalletIdRollbackOutputStream.write((new String(companyQueryBuilder).getBytes(StandardCharsets.UTF_8)));
        directDebitWalletIdRollbackOutputStream.write(";\n".getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void walletConverter() throws Exception {

        // DIRECT DEBIT
        createDirectDebitInputFileWithDetokenizedBankCode();
        directDebitSDTToWalletConversion();


    }

    private void createDirectDebitInputFileWithDetokenizedBankCode() throws Exception {
        // read from user home directory : input
        String path = System.getProperty("user.home") + File.separator + "Desktop";
        path += File.separator + "Converter/token";			//File dir = new File(xmlFilesDirectory);

        // creditCard_output_PB_*.csv to excel
        File latestInputFileCsv = ExcelUtil.getLastModified(path, "directDebit_decryptedBankcodeSheet", ".csv");
        if (latestInputFileCsv == null) {
            throw new Exception("file not found! Kindly provide the pb output file");
        }

        String getFileNameOnly = ExcelUtil.removeFileExtention(latestInputFileCsv.getName());
        ExcelUtil.convertCsvToXls(path, latestInputFileCsv.getAbsolutePath(), getFileNameOnly);

        File latestFileXlsx = ExcelUtil.getLastModified(path, "directDebit_decryptedBankcodeSheet", ".xlsx");
        if (latestFileXlsx == null) {
            throw new Exception("file not found! the pb output file: xlsx not created");
        }

        // TODO : read this excel and put it to hashmap which will later be used for lookup
        addDetokenizedValueWalletIdToMap(latestFileXlsx.getAbsolutePath());

        // TODO: read the input_pb excel and replace the bankcode with corresponding value in the cache map
        //keep file backup before overriding
        backupFileBeforeOverriding(latestFileXlsx, new File(path + "/directDebit_decryptedBankcodeSheet_backup.xlsx"));
        System.out.println("File backup done");

        //start reading overriding the input file bankcode with corresponding received detokenized value
        File latestInputFileXlsx = ExcelUtil.getLastModified(path, "directDebit_input_PB_", ".xlsx");

        createExcpectedInputFile(latestInputFileXlsx.getAbsolutePath(), path);

        // xlsx to csv converter
        File customDir = ExcelUtil.getUserHome();
        String inputFile = "_123.xlsx";
        File excel = new File(customDir + "/token/" + inputFile);

        ExcelUtil.convertXLXSFileToCSV(excel, 0, customDir, "directDebit_output_pb_" + System.currentTimeMillis()+ ".csv");
    }

    /**
     * https://wiki.intuit.com/display/EBSPaymentsStrategy/Converter+Service%3A+CSV+file+fields
     * @param absolutePath
     * @param path
     * @throws IOException
     */
    private void createExcpectedInputFile(String absolutePath, String path) throws IOException {
        // read accountnumber and decryptedddcardnumber from the input excel
        Map<String, Integer> accNumDDDecryptedCardNumMap = new HashMap<>();

        File excel = new File(absolutePath);

        FileInputStream fileInputStream = new FileInputStream(excel);
        XSSFWorkbook inputDDWorkbook = new XSSFWorkbook(fileInputStream);
        XSSFSheet inputDDWorkSheet = inputDDWorkbook.getSheetAt(0);

        FileOutputStream companiesNotPresentOutputStream = new FileOutputStream(path + "/_companies_not_present_123.xlsx");
        XSSFWorkbook companiesNotPresentDDWorkbook = new XSSFWorkbook();
        XSSFSheet companiesNotPresentDDWorkSheet = companiesNotPresentDDWorkbook.createSheet();


        FileOutputStream fileOutputStream = new FileOutputStream(path + "/_123.xlsx");
        XSSFWorkbook outputDDWorkbook = new XSSFWorkbook();
        XSSFSheet outputDDWorkSheet = outputDDWorkbook.createSheet();

        Row headers = inputDDWorkSheet.getRow(0); //read headers
        int ddRowCount = 0;
        Row ddHeaders = outputDDWorkSheet.createRow(ddRowCount++);

        int compNotDetokenizedCount = 0;
        Row companyNotDetokenizedHeaders = companiesNotPresentDDWorkSheet.createRow(compNotDetokenizedCount++);

        int cells = headers.getPhysicalNumberOfCells();
        for (int cellIndex = 0; cellIndex < cells; cellIndex++) {
            Cell cell = headers.getCell(cellIndex);

            // getting value from inputsheet and setting the value to outputsheet row / column
            ddHeaders.createCell(cellIndex).setCellValue(String.valueOf(cell));
            companyNotDetokenizedHeaders.createCell(cellIndex).setCellValue(String.valueOf(cell));

            switch (cell.toString().trim()) {
                case "accountId":
                    accNumDDDecryptedCardNumMap.put("accountId", cellIndex);                  // keys must match with headers
                    break;

                case "bankCode":
                    accNumDDDecryptedCardNumMap.put("bankCode", cellIndex);      // keys must match with headers
                    break;

                default:
            }
        }

        // now, iterate the remaining rows
        int rows = inputDDWorkSheet.getPhysicalNumberOfRows() - 1; // excluding headers
        for (int r = 1; r <= rows; r++) {
            Row sdtRow = inputDDWorkSheet.getRow(r);
            Row ddRow = outputDDWorkSheet.createRow(r);
            Row companiesNotPresentRow = companiesNotPresentDDWorkSheet.createRow(r);

            Cell bankcode = sdtRow.getCell(accNumDDDecryptedCardNumMap.get("bankCode")); // read directly the header values by their column index
            Cell accountId = sdtRow.getCell(accNumDDDecryptedCardNumMap.get("accountId")); // read directly the header values by their column index


            // now, lookup into the map as cache
            if (!detokenizePOJOMap.isEmpty() && detokenizePOJOMap.containsKey(accountId.getStringCellValue())) {
                // get corresponding detokenized value from the map
                bankcode.setCellValue(detokenizePOJOMap.get(accountId.getStringCellValue()).getDecryptedddcardnumber()); //overwriting the value
                System.out.println("Test Data From Excel : "+bankcode);
            } else {
                // get all cell values and write to new sheet
                for (int cellIndex = 0; cellIndex < cells; cellIndex++) {
                    Cell cell = sdtRow.getCell(cellIndex);
                    if (sdtRow != null) {
                        companiesNotPresentRow.createCell(cellIndex).setCellValue(String.valueOf(cell)); // read from input sheet and create the cell with exact value
                    }
                }
                continue;
            }

            // get all cell values and write to new sheet
            for (int cellIndex = 0; cellIndex < cells; cellIndex++) {
                Cell cell = sdtRow.getCell(cellIndex);


                if (sdtRow != null) {

                    // getting value from inputsheet and setting the value to outputsheet row / column
                    ddRow.createCell(cellIndex).setCellValue(String.valueOf(cell)); // read from input sheet and create the cell with exact value
                   // companiesNotPresentRow.createCell(cellIndex).setCellValue(String.valueOf(cell)); // read from input sheet and create the cell with exact value

                }
            }
        }

        outputDDWorkbook.write(fileOutputStream);
        outputDDWorkbook.close();

        companiesNotPresentDDWorkbook.write(companiesNotPresentOutputStream);
        companiesNotPresentDDWorkbook.close();

        inputDDWorkbook.close();
        fileInputStream.close();
        //companiesNotPresentOutputStream.close();

    }

    private void backupFileBeforeOverriding(File source, File dest) throws IOException {
        Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }


    private static void directDebitSDTToWalletConversion() throws Exception {

        // read from user home directory : input
        String path = System.getProperty("user.home") + File.separator + "Desktop";
        path += File.separator + "Converter";			//File dir = new File(xmlFilesDirectory);

        // creditCard_output_PB_*.csv to excel
        File latestFileCsv = ExcelUtil.getLastModified(path, "directDebit_output_PB_", ".csv");
        if (latestFileCsv == null) {
            throw new Exception("file not found! Kindly provide the pb output file");
        }

        String getFileNameOnly = ExcelUtil.removeFileExtention(latestFileCsv.getName());
        ExcelUtil.convertCsvToXls(path, latestFileCsv.getAbsolutePath(), getFileNameOnly);

        File latestFileXlsx = ExcelUtil.getLastModified(path, "directDebit_output_PB_", ".xlsx");
        if (latestFileXlsx == null) {
            throw new Exception("file not found! the pb output file: xlsx not created");
        }

        // now read the formatted output file and get values to create the queries
        DirectDebitSDTTOWalletConverter.createDirectDebitWalletIdQuery(latestFileXlsx.getAbsolutePath());
        System.out.println("query created");
    }

    static void addDetokenizedValueWalletIdToMap(String absolutePath) throws IOException {
        File excel = new File(absolutePath);

        FileInputStream fileInputStream = new FileInputStream(excel);
        XSSFWorkbook detokenizeWorkbook = new XSSFWorkbook(fileInputStream);
        XSSFSheet detokenizeSheet = detokenizeWorkbook.getSheetAt(0);

        Row headers = detokenizeSheet.getRow(0); //read headers

        int cells = headers.getPhysicalNumberOfCells();
        for (int cellIndex = 0; cellIndex < cells; cellIndex++) {
            Cell cell = headers.getCell(cellIndex);
            switch (cell.toString().trim()) {
                case "accountId":
                    sdtWalletDetokenizeHeadersMap.put("accountId", cellIndex);                  // keys must match with headers
                    break;

                case "decryptedddcardnumber":
                    sdtWalletDetokenizeHeadersMap.put("decryptedddcardnumber", cellIndex);      // keys must match with headers
                    break;

                case "encryptedbankcode":
                    sdtWalletDetokenizeHeadersMap.put("encryptedbankcode", cellIndex);          // keys must match with headers.. no need to store wallet id as it will be always null in case of error
                    break;

                default:
            }
        }

        // now, iterate the remaining rows
        int rows = detokenizeSheet.getPhysicalNumberOfRows() - 1; // excluding headers
        for (int r = 1; r <= rows; r++) {
            Row sdtRow = detokenizeSheet.getRow(r);

            if (sdtRow != null) {
                Cell accountId = sdtRow.getCell(sdtWalletDetokenizeHeadersMap.get("accountId")); // read directly the header values by their column index
                Cell decryptedddcardnumber = sdtRow.getCell(sdtWalletDetokenizeHeadersMap.get("decryptedddcardnumber")); // read directly the header values by their column index
                Cell encryptedbankcode = sdtRow.getCell(sdtWalletDetokenizeHeadersMap.get("encryptedbankcode")); // read directly the header values by their column index

                // now, put these values to map as cache
                DetokenizePOJO detokenizePOJO = new DetokenizePOJO();
                detokenizePOJO.setAccountId(accountId.getStringCellValue());
                detokenizePOJO.setDecryptedddcardnumber(decryptedddcardnumber.getStringCellValue());
                detokenizePOJO.setEncryptedbankcode(encryptedbankcode.getStringCellValue());
                detokenizePOJOMap.put(detokenizePOJO.getAccountId(), detokenizePOJO);

                System.out.println(detokenizePOJO);
            }
        }
    }
}
