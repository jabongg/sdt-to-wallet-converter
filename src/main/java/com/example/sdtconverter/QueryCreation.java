package com.example.sdtconverter;

import com.opencsv.CSVReader;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class QueryCreation {
    private  static String inputFilePath = "/Users/jpatel10/Desktop/query/input/Total_companyid_CardWalletId.csv";
    private  static String outputFilePathRollback ="/Users/jpatel10/Desktop/query/output/total-rollback-query-now.sql";
    private  static String outputFilePathUpdate ="/Users/jpatel10/Desktop/query/output/total-update-query-now.sql";

//    private  static String inputFilePath = "/Users/jpatel10/Desktop/query/input/PayrollFirst.csv";
//    private  static String outputFilePathRollback ="/Users/jpatel10/Desktop/query/output/PayrollFirst-rollback-query-now.sql";
//    private  static String outputFilePathUpdate ="/Users/jpatel10/Desktop/query/output/PayrollFirst-update-query-now.sql";


//    private  static String inputFilePath = "/Users/jpatel10/Desktop/query/input/NotPayrollFirst.csv";
//    private  static String outputFilePathRollback ="/Users/jpatel10/Desktop/query/output/NotPayrollFirst-rollback-query-now.sql";
//    private  static String outputFilePathUpdate ="/Users/jpatel10/Desktop/query/output/NotPayrollFirst-update-query-now.sql";

    public static void main(String[] args) {
        //read the '|' separated csv file and create the rollback and updated query.
        readCSVFileUpdateQuery();
        readCSVFileRollbackQuery();
    }

    private static void readCSVFileUpdateQuery() {
        try {
            // create a reader
            Reader reader = Files.newBufferedReader(Paths.get(inputFilePath));

            // create csv reader
            CSVReader csvReader = new CSVReader(reader);

            // write to different files
            // read one record at a time
            String[] record;
            StringBuilder updateQueryString = new StringBuilder();

            while ((record = csvReader.readNext()) != null) {
                String companyid_CardWalletId = record[0];
                String[] companyid_CardWalletIdArr = companyid_CardWalletId.split(Pattern.quote("|"));
                String companyID = companyid_CardWalletIdArr[0];
                String cardWalletId = companyid_CardWalletIdArr[1].substring(1);

                System.out.println(companyID + " " + cardWalletId);

               // update = new BufferedWriter(new FileWriter("/Users/jpatel10/Desktop/query/update-query.sql"));
                updateQueryString = updateQueryString.append(createUpdateQuery(companyID, cardWalletId));
            }

            Files.write(Paths.get(outputFilePathUpdate), new String(updateQueryString).getBytes(StandardCharsets.UTF_8));
            // close readers
            csvReader.close();
            reader.close();

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void readCSVFileRollbackQuery() {
        try {
            // create a reader
            Reader reader = Files.newBufferedReader(Paths.get(inputFilePath));

            // create csv reader
            CSVReader csvReader = new CSVReader(reader);

            // write to different files
            // read one record at a time
            String[] record;
            StringBuilder rollbackQueryString = new StringBuilder();
            while ((record = csvReader.readNext()) != null) {
                String companyid_CardWalletId = record[0];
                String[] companyid_CardWalletIdArr = companyid_CardWalletId.split(Pattern.quote("|"));
                String companyID = companyid_CardWalletIdArr[0];
                String cardWalletId = companyid_CardWalletIdArr[1].substring(1);

                System.out.println(companyID + " " + cardWalletId);

               // rollback = new BufferedWriter(new FileWriter("/Users/jpatel10/Desktop/query/rollback-query.sql"));
                rollbackQueryString = rollbackQueryString.append(createRollbackQuery(companyID, cardWalletId));

            }
            Files.write(Paths.get(outputFilePathRollback), new String(rollbackQueryString).getBytes(StandardCharsets.UTF_8));
            csvReader.close();
            reader.close();

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static StringBuilder createUpdateQuery(String companyID, String cardWalletId) {
        StringBuilder updatesb = new StringBuilder();

        if (!cardWalletId.isEmpty()) {
            updatesb.append("print" + " " + "'" + "companyId is :" + companyID + "'" + ";\n");
            updatesb.append("UPDATE dbo.CompanySecrets SET CardwalletId = null WHERE companyId =" + companyID + ";\n");
            updatesb.append("UPDATE dbo.Companies SET Version = Version + 1, hk_modified = GETDATE() WHERE companyID =" + companyID + ";\n");

        }
        else {
            updatesb.append("print" + " " + "'" + "companyId is :" + companyID + "'" + ";\n");
            updatesb.append("UPDATE dbo.CompanySecrets SET CardwalletId = null WHERE companyId =" + companyID + ";\n");
            updatesb.append("UPDATE dbo.Companies SET Version = Version + 1, hk_modified = GETDATE() WHERE companyID =" + companyID + ";\n");

        }
           return updatesb;
    }

    private static StringBuilder createRollbackQuery(String companyID, String cardWalletId) {
        StringBuilder rollbacksb = new StringBuilder();
        //cardWalletId = cardWalletId.isEmpty() ? null : cardWalletId;
        if (!cardWalletId.isEmpty()) {
            rollbacksb.append("print" + " " + "'" + "companyId is :" + companyID + "'" + ";\n");
            rollbacksb.append("UPDATE dbo.CompanySecrets SET CardwalletId = " + "'" + cardWalletId + "'" + " WHERE companyId =" + companyID + ";\n");
            rollbacksb.append("UPDATE dbo.Companies SET Version = Version + 1, hk_modified = GETDATE() WHERE companyID =" + companyID + ";\n");

        } else {
            rollbacksb.append("print" + " " + "'" + "companyId is :" + companyID + "'" + ";\n");
            rollbacksb.append("UPDATE dbo.CompanySecrets SET CardwalletId = null WHERE companyId =" + companyID + ";\n");
            rollbacksb.append("UPDATE dbo.Companies SET Version = Version + 1, hk_modified = GETDATE() WHERE companyID =" + companyID + ";\n");

        }
        return rollbacksb;
    }
}
