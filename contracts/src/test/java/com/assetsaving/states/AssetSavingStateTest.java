package com.assetsaving.states;

import net.corda.core.contracts.Amount;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.finance.Currencies;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.assetsaving.states.TestUtils.*;
import static groovy.util.GroovyTestCase.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.*;

public class AssetSavingStateTest {

    @Test
    public void hasAccumulationFieldOfCorrectType() throws NoSuchFieldException {
        // Does the amount field exist?
        Field amountField = AssetSavingState.class.getDeclaredField("accumulation");
        // Is the amount field of the correct type?
        assertTrue(amountField.getType().isAssignableFrom(Amount.class));
    }

    @Test
    public void hasBankFieldOfCorrectType() throws NoSuchFieldException {
        // Does the bank field exist?
        Field bankField = AssetSavingState.class.getDeclaredField("bank");
        // Is the bank field of the correct type?
        assertTrue(bankField.getType().isAssignableFrom(Party.class));
    }

    @Test
    public void hasCustomerFieldOfCorrectType() throws NoSuchFieldException {
        // Does the customer field exist?
        Field customerField = AssetSavingState.class.getDeclaredField("customer");
        // Is the customer field of the correct type?
        assertTrue(customerField.getType().isAssignableFrom(AnonymousParty.class));
    }

    @Test
    public void hasStartDateFieldOfCorrectType() throws NoSuchFieldException {
        // Does the customer field exist?
        Field startDateField = AssetSavingState.class.getDeclaredField("startDate");
        // Is the customer field of the correct type?
        assertTrue(startDateField.getType().isAssignableFrom(Date.class));
    }

    @Test
    public void bankIsParticipant() {
        AssetSavingState assetSavingState = new AssetSavingState(PartyA.getParty(), AccountA, new Date(), Currencies.POUNDS(0));
        assertNotEquals(assetSavingState.getParticipants().indexOf(PartyA.getParty()), -1);
    }

    @Test
    public void borrowerIsParticipant() {
        AssetSavingState assetSavingState = new AssetSavingState(PartyB.getParty(), AccountB, new Date(), Currencies.POUNDS(0));
        assertNotEquals(assetSavingState.getParticipants().indexOf(AccountB), -1);
    }

    @Test
    public void isLinearState() {
        assert(LinearState.class.isAssignableFrom(AssetSavingState.class));
    }

    @Test
    public void hasLinearIdFieldOfCorrectType() throws NoSuchFieldException {
        // Does the linearId field exist?
        Field linearIdField = AssetSavingState.class.getDeclaredField("linearId");

        // Is the linearId field of the correct type?
        assertTrue(linearIdField.getType().isAssignableFrom(UniqueIdentifier.class));
    }

    @Test
    public void checkIOUStateParameterOrdering() throws NoSuchFieldException {

        List<Field> fields = Arrays.asList(AssetSavingState.class.getDeclaredFields());

        int bankIdx = fields.indexOf(AssetSavingState.class.getDeclaredField("bank"));
        int customerIdx = fields.indexOf(AssetSavingState.class.getDeclaredField("customer"));
        int startDateIdx = fields.indexOf(AssetSavingState.class.getDeclaredField("startDate"));
        int accumulationIdx = fields.indexOf(AssetSavingState.class.getDeclaredField("accumulation"));
        int linearIdIdx = fields.indexOf(AssetSavingState.class.getDeclaredField("linearId"));

        assertTrue(bankIdx < customerIdx);
        assertTrue(customerIdx < startDateIdx);
        assertTrue(startDateIdx < accumulationIdx);
        assertTrue(accumulationIdx < linearIdIdx);
    }

    @Test
    public void checkWithNewAccumulationMethod() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState assetSavingState = new AssetSavingState(PartyA.getParty(), AccountA, dateFormat.parse("2021-12-12"), Currencies.POUNDS(0), new UniqueIdentifier());
        assertEquals(Currencies.DOLLARS(100L), assetSavingState.withNewAccumulation(100L).getAccumulation());
        assertEquals(Currencies.DOLLARS(200L), assetSavingState.withNewAccumulation(200L).getAccumulation());
    }

    @Test
    public void checkWithNewBankAndDateAndAccumulationMethod() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState assetSavingState = new AssetSavingState(PartyA.getParty(), AccountA, dateFormat.parse("2021-12-12"), Currencies.POUNDS(0), new UniqueIdentifier());
        assertEquals(PartyB.getParty(), assetSavingState.withNewBankAndDateAndAccumulation(PartyB.getParty(), AccountB, dateFormat.parse("2021-12-25"), 200L).getBank());
        assertEquals(AccountB, assetSavingState.withNewBankAndDateAndAccumulation(PartyB.getParty(), AccountB, dateFormat.parse("2021-12-25"), 200L).getCustomer());
        assertEquals(dateFormat.parse("2021-12-25"), assetSavingState.withNewBankAndDateAndAccumulation(PartyB.getParty(), AccountB, dateFormat.parse("2021-12-25"), 200L).getStartDate());
        assertEquals(Currencies.DOLLARS(200L), assetSavingState.withNewBankAndDateAndAccumulation(PartyB.getParty(), AccountB, dateFormat.parse("2021-12-25"), 200L).getAccumulation());
    }

    @Test
    public void correctConstructorsExist() {
        // Public constructor for new states
        try {
            Constructor<AssetSavingState> contructor = AssetSavingState.class.getConstructor(Party.class, AnonymousParty.class, Date.class, Amount.class);
        } catch( NoSuchMethodException nsme ) {
            fail("The correct public constructor does not exist!");
        }
        // Private constructor for updating states
        try {
            Constructor<AssetSavingState> contructor = AssetSavingState.class.getConstructor(Party.class, AnonymousParty.class, Date.class, Amount.class, UniqueIdentifier.class);
        } catch( NoSuchMethodException nsme ) {
            fail("The correct private copy constructor does not exist!");
        }
    }
}
