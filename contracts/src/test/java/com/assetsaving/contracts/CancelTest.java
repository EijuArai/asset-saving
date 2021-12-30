package com.assetsaving.contracts;

import com.assetsaving.states.AssetSavingState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.TypeOnlyCommandData;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.finance.Currencies;
import net.corda.testing.node.MockServices;
import org.junit.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;

import static com.assetsaving.states.TestUtils.*;
import static com.assetsaving.states.TestUtils.AccountD;
import static net.corda.testing.node.NodeTestUtils.ledger;

public class CancelTest {
    // A pre-defined dummy command.
    public interface Commands extends CommandData {
        class DummyCommand extends TypeOnlyCommandData implements CancelTest.Commands {}
    }

    static private final MockServices ledgerServices = new MockServices(
            Arrays.asList("com.assetsaving.contracts")
    );

    @Test
    public void mustIncludeCancelCommand() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState inputState = new AssetSavingState(PartyA.getParty(), AccountB, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), new UniqueIdentifier());

        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.command(Arrays.asList(PartyA.getPublicKey(), AccountB.getOwningKey()), new Commands.DummyCommand()); // Wrong type.
                return tx.failsWith("Contract verification failed");
            });
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.command(Arrays.asList(PartyA.getPublicKey(), AccountB.getOwningKey()), new AssetSavingContract.Commands.Cancel()); // Correct type.
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void transferTransactionMustHaveOneInputs() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState inputState = new AssetSavingState(PartyA.getParty(), AccountB, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), new UniqueIdentifier());

        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.output(AssetSavingContract.ID, inputState);
                tx.command(Arrays.asList(PartyA.getPublicKey(), AccountB.getOwningKey()), new AssetSavingContract.Commands.Cancel());
                return tx.failsWith("Only one input state should be consumed when cancel an AssetSaving.");
            });
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.input(AssetSavingContract.ID, inputState);
                tx.command(Arrays.asList(PartyA.getPublicKey(), AccountB.getOwningKey()), new AssetSavingContract.Commands.Cancel());
                return tx.failsWith("Only one input state should be consumed when cancel an AssetSaving.");
            });
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.command(Arrays.asList(PartyA.getPublicKey(), AccountB.getOwningKey()), new AssetSavingContract.Commands.Cancel());
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void transferTransactionMustHaveNoOutput() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState inputState = new AssetSavingState(PartyA.getParty(), AccountB, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), new UniqueIdentifier());
        AssetSavingState outputState = new AssetSavingState(PartyC.getParty(), AccountD, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), inputState.getLinearId());

        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.command(Arrays.asList(PartyA.getPublicKey(), AccountB.getOwningKey()), new AssetSavingContract.Commands.Cancel());
                tx.input(AssetSavingContract.ID, inputState);
                tx.output(AssetSavingContract.ID, outputState);
                return tx.failsWith("No output state should be created when cancel an AssetSaving.");
            });
            l.transaction(tx -> {
                tx.command(Arrays.asList(PartyA.getPublicKey(), AccountB.getOwningKey()), new AssetSavingContract.Commands.Cancel());
                tx.input(AssetSavingContract.ID, inputState);
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void bankAndCustomerMustSignIssueTransaction() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState inputState = new AssetSavingState(PartyA.getParty(), AccountB, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), new UniqueIdentifier());

        ledger(ledgerServices, l->{
            l.transaction(tx-> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.command(PartyA.getPublicKey(),  new AssetSavingContract.Commands.Cancel());
                return tx.failsWith("Both bank and customer together only may sign the AssetSaving cancel transaction.");
            });
            l.transaction(tx-> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.command(Arrays.asList(PartyA.getPublicKey(), PartyA.getPublicKey(), PartyA.getPublicKey()),  new AssetSavingContract.Commands.Cancel());
                return tx.failsWith("Both bank and customer together only may sign the AssetSaving cancel transaction.");
            });
            l.transaction(tx-> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.command(Arrays.asList(PartyA.getPublicKey(), AccountB.getOwningKey()), new AssetSavingContract.Commands.Cancel());
                return tx.verifies();
            });
            return null;
        });
    }

}
