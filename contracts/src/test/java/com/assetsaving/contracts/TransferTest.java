package com.assetsaving.contracts;

import com.assetsaving.states.AssetSavingState;
import com.assetsaving.states.TestUtils;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.TypeOnlyCommandData;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.finance.Currencies;
import net.corda.testing.node.MockServices;
import org.junit.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;

import static net.corda.testing.node.NodeTestUtils.ledger;

public class TransferTest {
    // A pre-defined dummy command.
    public interface Commands extends CommandData {
        class DummyCommand extends TypeOnlyCommandData implements IssueTest.Commands {}
    }

    static private final MockServices ledgerServices = new MockServices(
            Arrays.asList("com.template.contracts")
    );

    @Test
    public void mustIncludeTransferCommand() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState inputState = new AssetSavingState(TestUtils.PartyA.getParty(), TestUtils.AccountB, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), new UniqueIdentifier());
        AssetSavingState outputState = new AssetSavingState(TestUtils.PartyC.getParty(), TestUtils.AccountD, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), inputState.getLinearId());

        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.output(AssetSavingContract.ID, outputState);
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey()), new TransferTest.Commands.DummyCommand()); // Wrong type.
                return tx.failsWith("Contract verification failed");
            });
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.output(AssetSavingContract.ID, outputState);
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey(), TestUtils.PartyC.getPublicKey(), TestUtils.AccountD.getOwningKey()), new AssetSavingContract.Commands.Transfer()); // Correct type.
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void transferTransactionMustHaveOneInputs() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState inputState = new AssetSavingState(TestUtils.PartyA.getParty(), TestUtils.AccountB, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), new UniqueIdentifier());
        AssetSavingState outputState = new AssetSavingState(TestUtils.PartyC.getParty(), TestUtils.AccountD, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), inputState.getLinearId());

        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.output(AssetSavingContract.ID, outputState);
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey(), TestUtils.PartyC.getPublicKey(), TestUtils.AccountD.getOwningKey()), new AssetSavingContract.Commands.Transfer());
                return tx.failsWith("An AssetSaving transfer transaction should only consume one input state.");
            });
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.input(AssetSavingContract.ID, inputState);
                tx.output(AssetSavingContract.ID, outputState);
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey(), TestUtils.PartyC.getPublicKey(), TestUtils.AccountD.getOwningKey()), new AssetSavingContract.Commands.Transfer());
                return tx.failsWith("An AssetSaving transfer transaction should only consume one input state.");
            });

            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.output(AssetSavingContract.ID, outputState);
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey(), TestUtils.PartyC.getPublicKey(), TestUtils.AccountD.getOwningKey()), new AssetSavingContract.Commands.Transfer());
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void transferTransactionMustHaveOneOutput() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState inputState = new AssetSavingState(TestUtils.PartyA.getParty(), TestUtils.AccountB, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), new UniqueIdentifier());
        AssetSavingState outputState = new AssetSavingState(TestUtils.PartyC.getParty(), TestUtils.AccountD, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), inputState.getLinearId());

        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey(), TestUtils.PartyC.getPublicKey(), TestUtils.AccountD.getOwningKey()), new AssetSavingContract.Commands.Transfer());
                tx.input(AssetSavingContract.ID, inputState);
                tx.output(AssetSavingContract.ID, outputState);
                tx.output(AssetSavingContract.ID, outputState);
                return tx.failsWith("An AssetSaving transfer transaction should only create one output state.");
            });
            l.transaction(tx -> {
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey(), TestUtils.PartyC.getPublicKey(), TestUtils.AccountD.getOwningKey()), new AssetSavingContract.Commands.Transfer());
                tx.input(AssetSavingContract.ID, inputState);
                tx.output(AssetSavingContract.ID, outputState);
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void transferTransactionMustHaveDifferentBanks() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState inputState = new AssetSavingState(TestUtils.PartyA.getParty(), TestUtils.AccountB, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), new UniqueIdentifier());
        AssetSavingState outputState1 = new AssetSavingState(TestUtils.PartyA.getParty(), TestUtils.AccountD, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), inputState.getLinearId());
        AssetSavingState outputState2 = new AssetSavingState(TestUtils.PartyC.getParty(), TestUtils.AccountD, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), inputState.getLinearId());


        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.output(AssetSavingContract.ID, outputState1);
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey(), TestUtils.PartyA.getPublicKey(), TestUtils.AccountD.getOwningKey()), new AssetSavingContract.Commands.Transfer());
                return tx.failsWith("The bank of the input state should be different from the output state.");
            });
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.output(AssetSavingContract.ID, outputState2);
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey(), TestUtils.PartyC.getPublicKey(), TestUtils.AccountD.getOwningKey()), new AssetSavingContract.Commands.Transfer());
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void transferTransactionMustHaveDifferentCustomers() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState inputState = new AssetSavingState(TestUtils.PartyA.getParty(), TestUtils.AccountB, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), new UniqueIdentifier());
        AssetSavingState outputState1 = new AssetSavingState(TestUtils.PartyC.getParty(), TestUtils.AccountB, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), inputState.getLinearId());
        AssetSavingState outputState2 = new AssetSavingState(TestUtils.PartyC.getParty(), TestUtils.AccountD, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), inputState.getLinearId());


        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.output(AssetSavingContract.ID, outputState1);
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey(), TestUtils.PartyC.getPublicKey(), TestUtils.AccountB.getOwningKey()), new AssetSavingContract.Commands.Transfer());
                return tx.failsWith("The customer of the input state should be different from the output state.");
            });
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.output(AssetSavingContract.ID, outputState2);
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey(), TestUtils.PartyC.getPublicKey(), TestUtils.AccountD.getOwningKey()), new AssetSavingContract.Commands.Transfer());
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void startDateMustLaterThanToday() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState inputState = new AssetSavingState(TestUtils.PartyA.getParty(), TestUtils.AccountB, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), new UniqueIdentifier());
        AssetSavingState outputState1 = new AssetSavingState(TestUtils.PartyC.getParty(), TestUtils.AccountD, dateFormat.parse("2021-11-11"), Currencies.POUNDS(1), inputState.getLinearId());
        AssetSavingState outputState2 = new AssetSavingState(TestUtils.PartyC.getParty(), TestUtils.AccountD, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), inputState.getLinearId());


        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.output(AssetSavingContract.ID, outputState1);
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey(), TestUtils.PartyC.getPublicKey(), TestUtils.AccountD.getOwningKey()), new AssetSavingContract.Commands.Transfer());
                return tx.failsWith("The start day should be later than today.");
            });
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.output(AssetSavingContract.ID, outputState2);
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey(), TestUtils.PartyC.getPublicKey(), TestUtils.AccountD.getOwningKey()), new AssetSavingContract.Commands.Transfer());
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void linearIdMustNotChanged() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState inputState = new AssetSavingState(TestUtils.PartyA.getParty(), TestUtils.AccountB, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), new UniqueIdentifier());
        AssetSavingState outputState1 = new AssetSavingState(TestUtils.PartyC.getParty(), TestUtils.AccountD, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), new UniqueIdentifier());
        AssetSavingState outputState2 = new AssetSavingState(TestUtils.PartyC.getParty(), TestUtils.AccountD, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), inputState.getLinearId());


        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.output(AssetSavingContract.ID, outputState1);
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey(), TestUtils.PartyC.getPublicKey(), TestUtils.AccountD.getOwningKey()), new AssetSavingContract.Commands.Transfer());
                return tx.failsWith("The linearId must not be changed.");
            });
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.output(AssetSavingContract.ID, outputState2);
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey(), TestUtils.PartyC.getPublicKey(), TestUtils.AccountD.getOwningKey()), new AssetSavingContract.Commands.Transfer());
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void bankAndCustomerMustSignIssueTransaction() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState inputState = new AssetSavingState(TestUtils.PartyA.getParty(), TestUtils.AccountB, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), new UniqueIdentifier());
        AssetSavingState outputState = new AssetSavingState(TestUtils.PartyC.getParty(), TestUtils.AccountD, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), inputState.getLinearId());

        ledger(ledgerServices, l->{
            l.transaction(tx-> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.output(AssetSavingContract.ID, outputState);
                tx.command(TestUtils.PartyA.getPublicKey(),  new AssetSavingContract.Commands.Transfer());
                return tx.failsWith("The old and new customer account and, old and new bank must sign an AssetSaving transfer transaction");
            });
            l.transaction(tx-> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.output(AssetSavingContract.ID, outputState);
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey()),  new AssetSavingContract.Commands.Transfer());
                return tx.failsWith("The old and new customer account and, old and new bank must sign an AssetSaving transfer transaction");
            });
            l.transaction(tx-> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.output(AssetSavingContract.ID, outputState);
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey(), TestUtils.PartyC.getPublicKey()),  new AssetSavingContract.Commands.Transfer());
                return tx.failsWith("The old and new customer account and, old and new bank must sign an AssetSaving transfer transaction");
            });

            l.transaction(tx-> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.output(AssetSavingContract.ID, outputState);
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey(), TestUtils.PartyC.getPublicKey(), TestUtils.AccountD.getOwningKey()), new AssetSavingContract.Commands.Transfer());
                return tx.verifies();
            });
            return null;
        });
    }
}
