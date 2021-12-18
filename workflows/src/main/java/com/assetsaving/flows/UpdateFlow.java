package com.assetsaving.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.UtilitiesKt;
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount;
import com.r3.corda.lib.accounts.workflows.services.AccountService;
import com.sun.istack.NotNull;
import com.assetsaving.contracts.AssetSavingContract;
import com.assetsaving.states.AssetSavingState;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import com.assetsaving.contracts.AssetSavingContract.Commands.Update;
import net.corda.core.utilities.ProgressTracker;

import java.util.*;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireThat;

public class UpdateFlow {

    @InitiatingFlow
    @StartableByRPC
    public static class InitiatorFlow extends FlowLogic<SignedTransaction> {
        private final UniqueIdentifier stateLinearId;
        private final Long newAccumulation;
        private final String customer;

        public InitiatorFlow(UniqueIdentifier stateLinearId, Long newAccumulation, String customer) {
            this.stateLinearId = stateLinearId;
            this.newAccumulation = newAccumulation;
            this.customer = customer;
        }

        @Suspendable
        private AnonymousParty getPartyForAccount(String accountName) throws FlowException {
            AccountService accountService = UtilitiesKt.getAccountService(this);
            List<StateAndRef<AccountInfo>> accountList = accountService.accountInfo(accountName);
            if (accountList.size() == 0) {
                throw new FlowException("Account " + accountName + " doesn't exist");
            }
            return subFlow(new RequestKeyForAccount(accountList.get(0).getState().getData()));
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {

            // Retrieve the AssetSavingState from the vault using LinearStateQueryCriteria
            List<UUID> listOfLinearIds = new ArrayList<>();
            listOfLinearIds.add(stateLinearId.getId());
            QueryCriteria queryCriteria = new QueryCriteria.LinearStateQueryCriteria(null, listOfLinearIds);

            // Get the customer account.
            AnonymousParty customerAccount = getPartyForAccount(customer);

            // Get a reference to the inputState data that we are going to update.
            Vault.Page results = getServiceHub().getVaultService().queryBy(AssetSavingState.class, queryCriteria);
            StateAndRef inputStateAndRefToUpdate = (StateAndRef) results.getStates().get(0);
            AssetSavingState inputStateToUpdate = (AssetSavingState) inputStateAndRefToUpdate.getState().getData();

            // Get a reference to the notary service on our network and our key pair.
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            // Create a new TransactionBuilder object.
            final TransactionBuilder builder = new TransactionBuilder(notary);

            // Construct a update command to be added to the transaction.
            final Command<Update> updateCommand = new Command<>(
                    new Update(), inputStateToUpdate.getParticipants()
                    .stream().map(AbstractParty::getOwningKey)
                    .collect(Collectors.toList()));

            // Add the command to the transaction using the TransactionBuilder.
            builder.addCommand(updateCommand);

            // Add input and output states to flow using the TransactionBuilder.
            builder.addInputState(inputStateAndRefToUpdate);
            builder.addOutputState(inputStateToUpdate.withNewAccumulation(newAccumulation), AssetSavingContract.ID);

            // Verify and sign the transaction
            builder.verify(getServiceHub());

            // Sign the transaction by own public key.
            SignedTransaction partiallySignedTransaction = getServiceHub()
                    .signInitialTransaction(builder, getOurIdentity().getOwningKey());

            // Collect all of the required signatures from customer using the CollectSignaturesFlow
            AccountService accountService = UtilitiesKt.getAccountService(this);
            FlowSession session = initiateFlow(accountService.accountInfo(customer).get(0).getState().getData().getHost());

            final SignedTransaction fullySignedTransaction = subFlow(new CollectSignaturesFlow(partiallySignedTransaction,
                    Arrays.asList(session), Collections.singleton(getOurIdentity().getOwningKey())));

            return subFlow(new FinalityFlow(fullySignedTransaction, Collections.emptyList()));
        }
    }

    @InitiatedBy(UpdateFlow.InitiatorFlow.class)
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
                @NotNull
                protected void checkTransaction(SignedTransaction stx) {
                    requireThat(require -> {
                        ContractState output = stx.getTx().getOutputs().get(0).getData();
                        require.using("This must be an AssetSavingState transaction", output instanceof AssetSavingState);
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
