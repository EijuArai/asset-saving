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

public class UpdateTest {
    // A pre-defined dummy command.
    public interface Commands extends CommandData {
        class DummyCommand extends TypeOnlyCommandData implements IssueTest.Commands {}
    }

    static private final MockServices ledgerServices = new MockServices(
            Arrays.asList("com.assetsaving.contracts")
    );

    @Test
    public void mustIncludeUpdateCommand() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState assetSavingState = new AssetSavingState(TestUtils.PartyA.getParty(), TestUtils.AccountB, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), new UniqueIdentifier());

        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, assetSavingState);
                tx.output(AssetSavingContract.ID, assetSavingState.withNewAccumulation(10L));
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey()), new IssueTest.Commands.DummyCommand()); // Wrong type.
                return tx.failsWith("Contract verification failed");
            });
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, assetSavingState);
                tx.output(AssetSavingContract.ID, assetSavingState.withNewAccumulation(10L));
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey()), new AssetSavingContract.Commands.Update()); // Correct type.
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void updateTransactionMustHaveOneInputs() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState assetSavingState = new AssetSavingState(TestUtils.PartyA.getParty(), TestUtils.AccountB, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), new UniqueIdentifier());

        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.output(AssetSavingContract.ID, assetSavingState);
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey()), new AssetSavingContract.Commands.Update());
                return tx.failsWith("An AssetSaving update transaction should only consume one input state.");
            });
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, assetSavingState);
                tx.input(AssetSavingContract.ID, assetSavingState);
                tx.output(AssetSavingContract.ID, assetSavingState.withNewAccumulation(10L));
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey()), new AssetSavingContract.Commands.Update());
                return tx.failsWith("An AssetSaving update transaction should only consume one input state.");
            });

            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, assetSavingState);
                tx.output(AssetSavingContract.ID, assetSavingState.withNewAccumulation(10L));
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey()), new AssetSavingContract.Commands.Update());
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void updateTransactionMustHaveOneOutput() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState assetSavingState = new AssetSavingState(TestUtils.PartyA.getParty(), TestUtils.AccountB, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), new UniqueIdentifier());

        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey()), new AssetSavingContract.Commands.Update());
                tx.input(AssetSavingContract.ID, assetSavingState);
                tx.output(AssetSavingContract.ID, assetSavingState.withNewAccumulation(10L));
                tx.output(AssetSavingContract.ID, assetSavingState.withNewAccumulation(10L));
                return tx.failsWith("An AssetSaving update transaction should only create one output state.");
            });
            l.transaction(tx -> {
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey()), new AssetSavingContract.Commands.Update());
                tx.input(AssetSavingContract.ID, assetSavingState);
                tx.output(AssetSavingContract.ID, assetSavingState.withNewAccumulation(10L));
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void cannotCreateZeroValueAssetSavings() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState assetSavingState = new AssetSavingState(TestUtils.PartyA.getParty(), TestUtils.AccountB, dateFormat.parse("2021-12-12"), Currencies.POUNDS(1), new UniqueIdentifier());

        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey()), new AssetSavingContract.Commands.Issue());
                tx.output(AssetSavingContract.ID, assetSavingState.withNewAccumulation(0L)); // Zero amount fails.
                return tx.failsWith("The Amount of the accumulation should be larger than 0.");
            });
            l.transaction(tx -> {
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey()), new AssetSavingContract.Commands.Issue());
                tx.output(AssetSavingContract.ID, assetSavingState.withNewAccumulation(10L));
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void cannotCreateSameAccumulationAmountAssetSavings() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState assetSavingState = new AssetSavingState(TestUtils.PartyA.getParty(), TestUtils.AccountB, dateFormat.parse("2021-25-25"), Currencies.POUNDS(1), new UniqueIdentifier());

        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, assetSavingState);
                tx.output(AssetSavingContract.ID, assetSavingState);
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey()), new AssetSavingContract.Commands.Update());
                return tx.failsWith("The Amount of the accumulation should be changed.");
            });
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, assetSavingState);
                tx.output(AssetSavingContract.ID, assetSavingState.withNewAccumulation(10L));
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey()), new AssetSavingContract.Commands.Update());
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void cannotOtherPropertiesChanged() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState inputState = new AssetSavingState(TestUtils.PartyA.getParty(), TestUtils.AccountB, dateFormat.parse("2021-25-25"), Currencies.POUNDS(1), new UniqueIdentifier());
        AssetSavingState outputState1 = new AssetSavingState(TestUtils.PartyA.getParty(), TestUtils.AccountB, dateFormat.parse("2021-25-24"), Currencies.POUNDS(2), inputState.getLinearId());
        AssetSavingState outputState2 = new AssetSavingState(TestUtils.PartyC.getParty(), TestUtils.AccountB, dateFormat.parse("2021-25-25"), Currencies.POUNDS(2), inputState.getLinearId());
        AssetSavingState outputState3 = new AssetSavingState(TestUtils.PartyA.getParty(), TestUtils.AccountC, dateFormat.parse("2021-25-25"), Currencies.POUNDS(2), inputState.getLinearId());

        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.output(AssetSavingContract.ID, outputState1);
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey()), new AssetSavingContract.Commands.Update());
                return tx.failsWith("Other properties except accumulation must not be changed.");
            });
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.output(AssetSavingContract.ID, outputState2);
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey()), new AssetSavingContract.Commands.Update());
                return tx.failsWith("Other properties except accumulation must not be changed.");
            });
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.output(AssetSavingContract.ID, outputState3);
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey()), new AssetSavingContract.Commands.Update());
                return tx.failsWith("Other properties except accumulation must not be changed.");
            });
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, inputState);
                tx.output(AssetSavingContract.ID, inputState.withNewAccumulation(10L));
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey()), new AssetSavingContract.Commands.Update());
                return tx.verifies();
            });

            return null;
        });
    }


    @Test
    public void bankAndCustomerMustSignIssueTransaction() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState assetSavingState = new AssetSavingState(TestUtils.PartyA.getParty(), TestUtils.AccountB, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), new UniqueIdentifier());
        ledger(ledgerServices, l->{
            l.transaction(tx-> {
                tx.input(AssetSavingContract.ID, assetSavingState);
                tx.output(AssetSavingContract.ID, assetSavingState.withNewAccumulation(10L));
                tx.command(TestUtils.PartyA.getPublicKey(),  new AssetSavingContract.Commands.Update());
                return tx.failsWith("Both bank and customer together only may sign AssetSaving update transaction.");
            });
            l.transaction(tx-> {
                tx.input(AssetSavingContract.ID, assetSavingState);
                tx.output(AssetSavingContract.ID, assetSavingState.withNewAccumulation(10L));
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.PartyA.getPublicKey(), TestUtils.PartyA.getPublicKey()),  new AssetSavingContract.Commands.Update());
                return tx.failsWith("Both bank and customer together only may sign AssetSaving update transaction.");
            });
            l.transaction(tx-> {
                tx.input(AssetSavingContract.ID, assetSavingState);
                tx.output(AssetSavingContract.ID, assetSavingState.withNewAccumulation(10L));
                tx.command(Arrays.asList(TestUtils.PartyA.getPublicKey(), TestUtils.AccountB.getOwningKey()), new AssetSavingContract.Commands.Update());
                return tx.verifies();
            });
            return null;
        });
    }

}
