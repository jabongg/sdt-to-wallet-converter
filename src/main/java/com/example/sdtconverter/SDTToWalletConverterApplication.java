package com.example.sdtconverter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;

// @author : @jpatel10 June.4.2021
@SpringBootApplication
public class SDTToWalletConverterApplication {

	public static void main(String[] args) {
		SpringApplication.run(SDTToWalletConverterApplication.class, args);
		System.out.println("hello world!");


		/*
		 * Main Code begins here
		 *
		 */
		try {
			CSVReader.readAndCreateExcel();
			System.out.println("success!");

			// now read the formatted output file and get values to create the queries
			CSVReader.createCreditCardWalletIdQuery();
			CSVReader.createDirectDebitWalletIdQuery();
			System.out.println("query created");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
