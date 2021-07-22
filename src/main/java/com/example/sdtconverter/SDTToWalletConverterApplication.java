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

	public static void main(String[] args) throws Exception {
		SpringApplication.run(SDTToWalletConverterApplication.class, args);
		System.out.println("hello world!");

		/*
		 * Main Code begins here
		 *
		 */
		CreditCardSDTTOWalletConverter.ddRemediationWallet();

		CreditCardSDTTOWalletConverter.WalletConverter();
	}



}
