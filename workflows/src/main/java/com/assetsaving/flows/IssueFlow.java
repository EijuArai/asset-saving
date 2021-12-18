package com.assetsaving.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.assetsaving.flows.utilities.InstanceGenerateFlow;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount;
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount;
import com.assetsaving.contracts.AssetSavingContract;
import com.assetsaving.states.AssetSavingState;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import com.assetsaving.contracts.AssetSavingContract.Commands.Issue;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireThat;

public class IssueFlow {
    @InitiatingFlow
    @StartableByRPC
    public static class InitiatorFlow extends FlowLogic<SignedTransaction> {

        private final String customer;
        private final Date startDate;
        private final long accumulation;

        public InitiatorFlow(String customer, String startDate, long accumulation) throws ParseException {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            this.customer = customer;
            this.accumulation = accumulation;
            this.startDate = dateFormat.parse(startDate);
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // Generate accountinfo & AnonymousParty object for transaction.
            final StateAndRef<AccountInfo> accountStateRef = (StateAndRef<AccountInfo>) subFlow(new CreateAccount(customer));
            final AnonymousParty customerAccount = subFlow(new RequestKeyForAccount(
                    accountStateRef.getState().getData()));

            // Create AssetSavingState.
            final AssetSavingState state = subFlow(new InstanceGenerateFlow("USD", getOurIdentity(),
                    customerAccount, startDate, accumulation));

            // Get a reference to the notary service on our network and our key pair.
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            // Construct a issue command to be added to the transaction.
            final Command<Issue> issueCommand = new Command<>(
                    new Issue(), state.getParticipants()
                    .stream().map(AbstractParty::getOwningKey)
                    .collect(Collectors.toList()));

            // Create a new TransactionBuilder object.
            final TransactionBuilder builder = new TransactionBuilder(notary);

            // Add the asset saving as an output state, as well as a command to the transaction builder.
            builder.addOutputState(state, AssetSavingContract.ID);
            builder.addCommand(issueCommand);

            // Verify and sign it with our KeyPair.
            builder.verify(getServiceHub());
            SignedTransaction partiallySignedTransaction = getServiceHub().signInitialTransaction(builder, getOurIdentity().getOwningKey());

            // Collect the host's signature using the SignTransactionFlow.
            FlowSession session = initiateFlow(accountStateRef.getState().getData().getHost());
            final SignedTransaction fullySignedTransaction = subFlow(new CollectSignaturesFlow(partiallySignedTransaction,
                    Arrays.asList(session), Collections.singleton(getOurIdentity().getOwningKey())));

            // Finalise the transaction
            return subFlow(new FinalityFlow(fullySignedTransaction, Collections.emptyList()));
        }
    }

    @InitiatedBy(IssueFlow.InitiatorFlow.class)
    public static class ResponderFlow extends FlowLogic<Void> {

        private final FlowSession otherPartyFlow;
        private SecureHash txWeJustSignedId;

        public ResponderFlow(FlowSession otherPartyFlow) {
            this.otherPartyFlow = otherPartyFlow;
        }

        @Suspendable
        @Override
        public Void call() throws FlowException {

            class SignTxFlow extends SignTransactionFlow {

                private SignTxFlow(FlowSession flowSession, ProgressTracker progressTracker) {
                    super(flowSession, progressTracker);
                }

                @Override
                protected void checkTransaction(SignedTransaction stx) {
                    requireThat(req -> {
                        ContractState output = stx.getTx().getOutputs().get(0).getData();
                        req.using("This must be an AssetSavingState transaction", output instanceof AssetSavingState);
                        return null;
                    });
                    // Once the transaction has verified, initialize txWeJustSignedID variable.
                    txWeJustSignedId = stx.getId();
                }
            }

//            otherPartyFlow.getCounterpartyFlowInfo().getFlowVersion();

            // Create a sign transaction flow
            SignTxFlow signTxFlow = new SignTxFlow(otherPartyFlow, SignTransactionFlow.Companion.tracker());

            // Run the sign transaction flow to sign the transaction
            subFlow(signTxFlow);

            // Run the ReceiveFinalityFlow to finalize the transaction and persist it to the vault.
            if (!otherPartyFlow.getCounterparty().equals(getOurIdentity())) {
                subFlow(new ReceiveFinalityFlow(otherPartyFlow, txWeJustSignedId));
            }

            return null;
        }
    }

}
