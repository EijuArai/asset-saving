package com.assetsaving.contracts;

import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.TypeOnlyCommandData;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.finance.Currencies;
import net.corda.testing.node.MockServices;
import org.junit.Test;
import com.assetsaving.states.AssetSavingState;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;

import static com.assetsaving.states.TestUtils.*;
import static net.corda.testing.node.NodeTestUtils.ledger;

public class IssueTest {
    // A pre-defined dummy command.
    public interface Commands extends CommandData {
        class DummyCommand extends TypeOnlyCommandData implements Commands{}
    }

    static private final MockServices ledgerServices = new MockServices(
            Arrays.asList("com.template.contracts")
    );

    @Test
    public void mustIncludeIssueCommand() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState assetSavingState = new AssetSavingState(PartyA.getParty(), AccountB, dateFormat.parse("2021-12-12"), Currencies.POUNDS(1), new UniqueIdentifier());

        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.output(AssetSavingContract.ID, assetSavingState);
                tx.command(Arrays.asList(PartyA.getPublicKey(), AccountB.getOwningKey()), new Commands.DummyCommand()); // Wrong type.
                return tx.failsWith("Contract verification failed");
            });
            l.transaction(tx -> {
                tx.output(AssetSavingContract.ID, assetSavingState);
                tx.command(Arrays.asList(PartyA.getPublicKey(), AccountB.getOwningKey()), new AssetSavingContract.Commands.Issue()); // Correct type.
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void issueTransactionMustHaveNoInputs() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState assetSavingState = new AssetSavingState(PartyA.getParty(), AccountB, dateFormat.parse("2021-12-12"), Currencies.POUNDS(1), new UniqueIdentifier());

        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.input(AssetSavingContract.ID, assetSavingState);
                tx.output(AssetSavingContract.ID, assetSavingState);
                tx.command(Arrays.asList(PartyA.getPublicKey(), AccountB.getOwningKey()), new AssetSavingContract.Commands.Issue());
                return tx.failsWith("No inputs should be consumed when issuing an AssetSaving.");
            });
            l.transaction(tx -> {
                tx.output(AssetSavingContract.ID, assetSavingState);
                tx.command(Arrays.asList(PartyA.getPublicKey(), AccountB.getOwningKey()), new AssetSavingContract.Commands.Issue());
                return tx.verifies(); // As there are no input sates
            });
            return null;
        });
    }

    @Test
    public void issueTransactionMustHaveOneOutput() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState assetSavingState = new AssetSavingState(PartyA.getParty(), AccountB, dateFormat.parse("2021-12-12"), Currencies.POUNDS(1), new UniqueIdentifier());

        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.command(Arrays.asList(PartyA.getPublicKey(), AccountB.getOwningKey()), new AssetSavingContract.Commands.Issue());
                tx.output(AssetSavingContract.ID, assetSavingState);
                tx.output(AssetSavingContract.ID, assetSavingState);
                return tx.failsWith("Only one output state should be created when issuing an AssetSaving.");
            });
            l.transaction(tx -> {
                tx.command(Arrays.asList(PartyA.getPublicKey(), AccountB.getOwningKey()), new AssetSavingContract.Commands.Issue());
                tx.output(AssetSavingContract.ID, assetSavingState);
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void cannotCreateZeroValueAssetSavings() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState assetSavingState = new AssetSavingState(PartyA.getParty(), AccountB, dateFormat.parse("2021-12-12"), Currencies.POUNDS(1), new UniqueIdentifier());

        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.command(Arrays.asList(PartyA.getPublicKey(), AccountB.getOwningKey()), new AssetSavingContract.Commands.Issue());
                tx.output(AssetSavingContract.ID, assetSavingState.withNewAccumulation(0L)); // Zero amount fails.
                return tx.failsWith("The Amount of the accumulation should be larger than 0.");
            });
            l.transaction(tx -> {
                tx.command(Arrays.asList(PartyA.getPublicKey(), AccountB.getOwningKey()), new AssetSavingContract.Commands.Issue());
                tx.output(AssetSavingContract.ID, assetSavingState.withNewAccumulation(10L));
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void cannotCreateEarlierStartDateAssetSavings() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState assetSavingState = new AssetSavingState(PartyA.getParty(), AccountB, dateFormat.parse("2021-01-01"), Currencies.POUNDS(1), new UniqueIdentifier());

        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.command(Arrays.asList(PartyA.getPublicKey(), AccountB.getOwningKey()), new AssetSavingContract.Commands.Issue());
                tx.output(AssetSavingContract.ID, assetSavingState);
                return tx.failsWith("The start day should be later than today.");
            });
            return null;
        });
    }

//    @Test
//    public void lenderAndBorrowerCannotBeTheSame() throws ParseException {
//        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
//        AssetSavingState assetSavingState = new AssetSavingState(PartyA.getParty(), AccountA, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), new UniqueIdentifier());
//        ledger(ledgerServices, l-> {
//            l.transaction(tx -> {
//                tx.command(Arrays.asList(PartyA.getParty().getOwningKey(), AccountA.getOwningKey()), new AssetSavingContract.Commands.Issue());
//                tx.output(AssetSavingContract.ID, assetSavingState);
//                return tx.failsWith("The lender and borrower cannot have the same identity.");
//            });
//            l.transaction(tx -> {
//                tx.command(Arrays.asList(PartyA.getParty().getOwningKey(), AccountB.getOwningKey()), new AssetSavingContract.Commands.Issue());
//                tx.output(AssetSavingContract.ID, assetSavingState);
//                return tx.verifies();
//            });
//            return null;
//        });
//    }

    @Test
    public void bankAndCustomerMustSignIssueTransaction() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AssetSavingState assetSavingState = new AssetSavingState(PartyA.getParty(), AccountB, dateFormat.parse("2021-12-25"), Currencies.POUNDS(1), new UniqueIdentifier());
        ledger(ledgerServices, l->{
            l.transaction(tx-> {
                tx.command(PartyA.getPublicKey(),  new AssetSavingContract.Commands.Issue());
                tx.output(AssetSavingContract.ID, assetSavingState);
                return tx.failsWith("Both bank and customer together only may sign AssetSaving issue transaction.");
            });
            l.transaction(tx-> {
                tx.command(Arrays.asList(PartyA.getPublicKey(), PartyA.getPublicKey(), PartyA.getPublicKey()),  new AssetSavingContract.Commands.Issue());
                tx.output(AssetSavingContract.ID, assetSavingState);
                return tx.failsWith("Both bank and customer together only may sign AssetSaving issue transaction.");
            });
            l.transaction(tx-> {
                tx.command(Arrays.asList(PartyA.getPublicKey(), AccountB.getOwningKey()), new AssetSavingContract.Commands.Issue());
                tx.output(AssetSavingContract.ID, assetSavingState);
                return tx.verifies();
            });
            return null;
        });
    }
}
