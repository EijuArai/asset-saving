package com.assetsaving.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.UtilitiesKt;
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount;
import com.r3.corda.lib.accounts.workflows.services.AccountService;
import com.assetsaving.states.AssetSavingState;
import net.corda.core.contracts.*;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import com.assetsaving.contracts.AssetSavingContract.Commands.*;

import java.util.*;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireThat;

public class CancelFlow {
    @InitiatingFlow
    @StartableByRPC
    public static class InitiatorFlow extends FlowLogic<SignedTransaction> {

        private final UniqueIdentifier stateLinearId;
        private final String customer;

        public InitiatorFlow(UniqueIdentifier stateLinearId, String customer) {
            this.stateLinearId = stateLinearId;
            this.customer = customer;
        }

        @Suspendable
        private AnonymousParty getPartyForAccount(String accountName) throws FlowException {
            AccountService accountService = UtilitiesKt.getAccountService(this);
            List<StateAndRef<AccountInfo>> accountList = accountService.accountInfo(accountName);
            if(accountList.size()==0){
                throw new FlowException("Account "+ accountName +" doesn't exist");
            }
            return subFlow(new RequestKeyForAccount(accountList.get(0).getState().getData()));
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {

            // Get the customer account.
            AnonymousParty customerAccount = getPartyForAccount(customer);

            // Retrieve the asset saving State from the vault using LinearStateQueryCriteria
            List<UUID> listOfLinearIds = Arrays.asList(stateLinearId.getId());
            QueryCriteria queryCriteria = new QueryCriteria.LinearStateQueryCriteria(null, listOfLinearIds);
            Vault.Page results = getServiceHub().getVaultService().queryBy(AssetSavingState.class, queryCriteria);

            // Get a reference to the inputState data that we are going to cancel.
            StateAndRef inputStateAndRefToCancel = (StateAndRef) results.getStates().get(0);
            AssetSavingState inputStateToCancel = (AssetSavingState) ((StateAndRef) results.getStates().get(0)).getState().getData();

            // Get a reference to the default notary and instantiate a transaction builder.
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
            final TransactionBuilder builder = new TransactionBuilder(notary);

            // Construct a cancel command to be added to the transaction.
            final Command<Cancel> command = new Command<>(
                    new Cancel(),
                    inputStateToCancel.getParticipants()
                            .stream().map(AbstractParty::getOwningKey)
                            .collect(Collectors.toList())
            );

            // Add the command and the input state to the transaction using the TransactionBuilder.
            builder.addCommand(command);
            builder.addInputState(inputStateAndRefToCancel);

            // Verify and sign the transaction
            builder.verify(getServiceHub());
            final SignedTransaction stx = getServiceHub().signInitialTransaction(builder, getOurIdentity().getOwningKey());

            // Collect all of the required signatures from other Corda nodes using the CollectSignaturesFlow
            AccountService accountService = UtilitiesKt.getAccountService(this);
            // Get the session with the node the customer belongs to.
            FlowSession session = initiateFlow(accountService.accountInfo(customer).get(0).getState().getData().getHost());
            SignedTransaction fullySignedTransaction = subFlow(new CollectSignaturesFlow(stx, Arrays.asList(session), Collections.singleton(getOurIdentity().getOwningKey())));

            return subFlow(new FinalityFlow(fullySignedTransaction, Collections.emptyList()));

        }

    }

    /**
     * This is the flow which signs IOU settlements.
     * The signing is handled by the [SignTransactionFlow].
     */
    @InitiatedBy(CancelFlow.InitiatorFlow.class)
    public static class Responder extends FlowLogic<Void> {

        private final FlowSession otherPartyFlow;
        private SecureHash txWeJustSignedId;

        public Responder(FlowSession otherPartyFlow) {
            this.otherPartyFlow = otherPartyFlow;
        }

        @Suspendable
        @Override
        public Void call() throws FlowException {
            class SignTxFlow extends SignTransactionFlow {
                private SignTxFlow(FlowSession otherPartyFlow, ProgressTracker progressTracker) {
                    super(otherPartyFlow, progressTracker);
                }

                @Override
                protected void checkTransaction(SignedTransaction stx) {
                    requireThat(require -> {
                        Boolean isOutputEmpty = stx.getTx().getOutputStates().size() == 0;
                        require.using("This transaction must has no output.", isOutputEmpty);
                        return null;
                    });
                    // Once the transaction has verified, initialize txWeJustSignedID variable.
                    txWeJustSignedId = stx.getId();
                }
            }

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
