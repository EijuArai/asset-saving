package com.assetsaving.flows.utilities;

import net.corda.core.serialization.CordaSerializable;

import java.util.Date;

@CordaSerializable
public class AssetSavingStateModel {

    private String bank;
    private String customer;

    public AssetSavingStateModel(String bank, String customer) {
        this.bank = bank;
        this.customer = customer;
    }

    public String getBank() {
        return bank;
    }

    public String getCustomer() {
        return customer;
    }

    @Override
    public String toString() {
        return "\n" +
                "AssetSavingStateModel{" + "\n" +
                "bank= " + bank + "\n" +
                ", customer= " + customer + "\n" +
                '}';
    }
}
