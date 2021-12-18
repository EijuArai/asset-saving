package com.assetsaving.states;

import com.assetsaving.contracts.AssetSavingContract;
import net.corda.core.contracts.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.serialization.ConstructorForDeserialization;
import net.corda.finance.Currencies;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Currency;
import java.util.Date;
import java.util.List;

@BelongsToContract(AssetSavingContract.class)
public class AssetSavingState implements ContractState, LinearState {

    //private variables
    private final Party bank;
    private final AnonymousParty customer;
    private final Date startDate;
    private final Amount<Currency> accumulation;
    private final UniqueIdentifier linearId;

    /* Constructor of your Corda state */
    @ConstructorForDeserialization
    public AssetSavingState(Party bank, AnonymousParty customer, Date startDate, Amount<Currency> accumulation, UniqueIdentifier linearId) {
        this.bank = bank;
        this.customer = customer;
        this.startDate = startDate;
        this.accumulation = accumulation;
        this.linearId = linearId;
    }

    public AssetSavingState(Party bank, AnonymousParty customer, Date startDate, Amount<Currency> accumulation) {
        this.bank = bank;
        this.customer = customer;
        this.startDate = startDate;
        this.accumulation = accumulation;
        this.linearId = new UniqueIdentifier();
    }

    //getters
    public Party getBank() {
        return bank;
    }

    public AnonymousParty getCustomer() {
        return customer;
    }

    public Date getStartDate() {
        return startDate;
    }

    public Amount<Currency> getAccumulation() {
        return accumulation;
    }

    public AssetSavingState withNewAccumulation (Long newAccumulation) {
        return new AssetSavingState(bank, customer, startDate, Currencies.DOLLARS(newAccumulation), linearId);
    }

    public AssetSavingState withNewBankAndDateAndAccumulation (Party newBank, AnonymousParty newCustomer, Date newDate, Long newAccumulation) {
        return new AssetSavingState(newBank, newCustomer, newDate, Currencies.DOLLARS(newAccumulation), linearId);
    }

    @NotNull
    @Override
    public UniqueIdentifier getLinearId() {
        return linearId;
    }

    /* This method will indicate who are the participants and required signers when
     * this state is used in a transaction. */
    @Override
    public List<AbstractParty> getParticipants() {
        return Arrays.asList(bank, customer);
    }
}
