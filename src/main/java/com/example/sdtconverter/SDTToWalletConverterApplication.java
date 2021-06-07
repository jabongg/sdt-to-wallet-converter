package com.example.sdtconverter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;

// @author : @jpatel10 June.4.2021
@SpringBootApplication
public class SDTToWalletConverterApplication {

	private static final  String SDT_FILE_PATH_CREDIT_CARD = "SDT_TO_wallet_conversion_Credit_Card.xlsx";
	private static final  String SDT_FILE_PATH_DIRECT_DEBIT = "SDT_TO_wallet_conversion_Direct_Debit.xlsx";
	private static final  String FORMATTED_OUTPUT_CREDIT_CARD = "formatted-output-credit-card.xlsx";
	private static final  String FORMATTED_OUTPUT_DIRECT_DEBIT = "formatted-output-direct-debit.xlsx";

	public static void main(String[] args) {
		SpringApplication.run(SDTToWalletConverterApplication.class, args);
		System.out.println("hello world!");

		/*
		 * Main Code begins here
		 *
		 */
		try {
			// read from user home directory : input
			String path = System.getProperty("user.home") + File.separator + "Desktop";
			path += File.separator + "Converter";			//File dir = new File(xmlFilesDirectory);
			//creditCard_output_PB_7jun_41k_1

			File latestFile = ExcelUtil.getLastModified(path);
			if (latestFile == null) {
				throw new Exception("file not found! Kindly provide the pb output file");
			}

			// CREDTI CARD
			CreditCardSDTTOWalletConverter.formatExcelToColumns(latestFile.getAbsolutePath(), FORMATTED_OUTPUT_CREDIT_CARD);
			System.out.println("success!");
			// now read the formatted output file and get values to create the queries
			//CreditCardSDTTOWalletConverter.createCreditCardWalletIdQuery(FORMATTED_OUTPUT_CREDIT_CARD);
			System.out.println("query created");

/*
			// DIRECT DEBIT
			DirectDebitSDTTOWalletConverter.formatExcelToColumns(SDT_FILE_PATH_DIRECT_DEBIT, FORMATTED_OUTPUT_DIRECT_DEBIT);
			System.out.println("success!");
			// now read the formatted output file and get values to create the queries
			DirectDebitSDTTOWalletConverter.createDirectDebitWalletIdQuery(FORMATTED_OUTPUT_DIRECT_DEBIT);
			System.out.println("query created");
*/

		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
