package com.example.sdtconverter;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.util.ObjectUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

// @author : @jpatel10 June.6.2021

public class DirectDebitSDTTOWalletConverter implements WalletConverter {

    private static Logger logger = Logger.getLogger(DirectDebitSDTTOWalletConverter.class.getName());
    private static Map<String, Integer> sdtWalletHeadersMap = new HashMap<>(); // to store imporatant columns which required in queries or error codes case

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
                    sdtWalletHeadersMap.put("walletId", cellIndex);                  // keys must match with headers.. no need to store wallet id as it will be always null i case of error
                    break;
                case "bankCode": // for routing number, which value to be mapped from input file  : is it bankCode?
                    sdtWalletHeadersMap.put("bankCode", cellIndex);                  // keys must match with headers.. no need to store wallet id as it will be always null i case of error
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
        DirectDebitSDTToWalletConversion();
    }


    private static void DirectDebitSDTToWalletConversion() throws Exception {

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
        //CreditCardSDTTOWalletConverter.addDetokenizedValueWalletId(latestFileXlsx.getAbsolutePath()); //TODO
        //System.out.println("file with detokenized value is created");


        //DirectDebitSDTTOWalletConverter.formatExcelToColumns("SDT_FILE_PATH_DIRECT_DEBIT", "FORMATTED_OUTPUT_DIRECT_DEBIT"); //TODO
        // System.out.println("success!");
        // now read the formatted output file and get values to create the queries
        DirectDebitSDTTOWalletConverter.createDirectDebitWalletIdQuery(latestFileXlsx.getAbsolutePath());
        System.out.println("query created");
    }

}
