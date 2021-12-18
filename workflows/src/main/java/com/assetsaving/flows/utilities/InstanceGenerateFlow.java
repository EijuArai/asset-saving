package com.assetsaving.flows.utilities;

import co.paralleluniverse.fibers.Suspendable;
import com.assetsaving.states.AssetSavingState;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.finance.Currencies;

import java.util.Date;

@StartableByRPC
public class InstanceGenerateFlow extends FlowLogic<AssetSavingState> {

    String currency;
    private long accumulation;
    Party bank;
    AnonymousParty customer;
    Date startDate;

    public InstanceGenerateFlow(String currency, Party bank, AnonymousParty customer, Date startDate, long accumulation) {
        this.currency = currency;
        this.accumulation = accumulation;
        this.bank = bank;
        this.customer = customer;
        this.startDate = startDate;
    }

    @Suspendable
    @Override
    public AssetSavingState call() throws FlowException {
        switch (currency) {
            case "USD":
                // generate IOUState for USD
                return new AssetSavingState(bank, customer, startDate, Currencies.DOLLARS(accumulation));
            case "GBP":
                // generate IOUState for GBP
                return new AssetSavingState(bank, customer, startDate, Currencies.POUNDS(accumulation));
            case "CHF":
                // generate IOUState for CHF
                return new AssetSavingState(bank, customer, startDate, Currencies.SWISS_FRANCS(accumulation));
            default:
                // Any currency other than the above will result in an error.
                throw new FlowException("Incorrect value for currency.Please set \"USD\", \"GBP\", or \"CHF\".");
        }
    }
}
