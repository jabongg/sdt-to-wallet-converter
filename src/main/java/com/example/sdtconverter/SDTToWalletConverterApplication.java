package com.example.sdtconverter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;

@SpringBootApplication
public class SDTToWalletConverterApplication {

	public static void main(String[] args) {
		SpringApplication.run(SDTToWalletConverterApplication.class, args);
		System.out.println("hello world!");
		try {
			CSVReader.readCsv();
			System.out.println("success!");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
