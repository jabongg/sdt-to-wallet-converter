package com.example.sdtconverter;

public class DetokenizePOJO {
    // keeping values of the csv file
    String accountId;
    String decryptedddcardnumber;
    String encryptedbankcode;

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getDecryptedddcardnumber() {
        return decryptedddcardnumber;
    }

    public void setDecryptedddcardnumber(String decryptedddcardnumber) {
        this.decryptedddcardnumber = decryptedddcardnumber;
    }

    public String getEncryptedbankcode() {
        return encryptedbankcode;
    }

    public void setEncryptedbankcode(String encryptedbankcode) {
        this.encryptedbankcode = encryptedbankcode;
    }
}
