package com.example.sdtconverter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;

// @author : @jpatel10 June.4.2021
@SpringBootApplication
public class SDTToWalletConverterApplication {

	private static final  String SDT_FILE_PATH_CREDIT_CARD = "SDT_TO_wallet_conversion.xlsx";
	// private static final  String SDT_FILE_PATH_DIRECT_DEBIT = "SDT_TO_wallet_conversion.xlsx";

	public static void main(String[] args) {
		SpringApplication.run(SDTToWalletConverterApplication.class, args);
		System.out.println("hello world!");

		/*
		 * Main Code begins here
		 *
		 */
		try {
			// CREDTI CARD
			CreditCardSDTTOWalletConverter.formatExcelToColumns(SDT_FILE_PATH_CREDIT_CARD);
			System.out.println("success!");
			// now read the formatted output file and get values to create the queries
			CreditCardSDTTOWalletConverter.createCreditCardWalletIdQuery();
			System.out.println("query created");


			// DIRECT DEBIT
			CreditCardSDTTOWalletConverter.formatExcelToColumns(SDT_FILE_PATH_CREDIT_CARD);
			System.out.println("success!");
			// now read the formatted output file and get values to create the queries
			DirectDebitSDTTOWalletConverter.createDirectDebitWalletIdQuery();
			System.out.println("query created");



		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
