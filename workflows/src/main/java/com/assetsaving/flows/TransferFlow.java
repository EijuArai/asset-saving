package com.assetsaving.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.UtilitiesKt;
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount;
import com.r3.corda.lib.accounts.workflows.services.AccountService;
import com.assetsaving.contracts.AssetSavingContract;
import com.assetsaving.flows.utilities.KickCreateAccountFlow;
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
import com.assetsaving.contracts.AssetSavingContract.Commands.Transfer;
import net.corda.core.utilities.ProgressTracker;

import java.security.PublicKey;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireThat;

public class TransferFlow {
    @InitiatingFlow
    @StartableByRPC
    public static class InitiatorFlow extends FlowLogic<SignedTransaction> {
        private final UniqueIdentifier stateLinearId;
        private final Party newBank;
        private final String customer;
        private final String newCustomer;
        private final Date newDate;
        private final Long newAccumulation;

        public InitiatorFlow(UniqueIdentifier stateLinearId, Party newBank, String customer, String newCustomer, String newDate, Long newAccumulation) throws ParseException {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            this.stateLinearId = stateLinearId;
            this.newBank = newBank;
            this.customer = customer;
            this.newCustomer = newCustomer;
            this.newDate = dateFormat.parse(newDate);
            this.newAccumulation = newAccumulation;

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

            // Kick the counter party to create and share the account.
            subFlow(new KickCreateAccountFlow.InitiatorFlow(newBank, newCustomer));

            // Get own account.
            AnonymousParty customerAccount = getPartyForAccount(customer);

            // Get destination party's account.
            AnonymousParty newCustomerAccount = getPartyForAccount(newCustomer);

            // Retrieve the AssetSavingState from the vault using LinearStateQueryCriteria
            List<UUID> listOfLinearIds = new ArrayList<>();
            listOfLinearIds.add(stateLinearId.getId());
            QueryCriteria queryCriteria = new QueryCriteria.LinearStateQueryCriteria(null, listOfLinearIds);

            // Get a reference to the inputState data that we are going to transfer.
            Vault.Page results = getServiceHub().getVaultService().queryBy(AssetSavingState.class, queryCriteria);
            StateAndRef inputStateAndRefToTransfer = (StateAndRef) results.getStates().get(0);
            AssetSavingState inputStateToTransfer = (AssetSavingState) inputStateAndRefToTransfer.getState().getData();

            // Get a reference to the notary service on our network and our key pair.
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            // Create a new TransactionBuilder object.
            final TransactionBuilder builder = new TransactionBuilder(notary);

            // Get the list of required signers.
            List<PublicKey> listOfRequiredSigners = inputStateToTransfer.getParticipants()
                    .stream().map(AbstractParty::getOwningKey)
                    .collect(Collectors.toList());
            listOfRequiredSigners.add(newBank.getOwningKey());
            listOfRequiredSigners.add(newCustomerAccount.getOwningKey());

            // Construct a transfer command to be added to the transaction.
            final Command<Transfer> transferCommand = new Command<>(
                    new Transfer(), listOfRequiredSigners);

            // Add the command to the transaction using the TransactionBuilder.
            builder.addCommand(transferCommand);

            // Add input and output states to flow using the TransactionBuilder.
            builder.addInputState(inputStateAndRefToTransfer);
            builder.addOutputState(inputStateToTransfer.withNewBankAndDateAndAccumulation(newBank, newCustomerAccount, newDate, newAccumulation),
                    AssetSavingContract.ID);

            // Verify and sign the transaction
            builder.verify(getServiceHub());
            SignedTransaction partiallySignedTransaction = getServiceHub().signInitialTransaction(builder, getOurIdentity().getOwningKey());

            // Share the account with the new bank.
            AccountService accountService = UtilitiesKt.getAccountService(this);
//            List<StateAndRef<AccountInfo>> accountInfoList = accountService.accountInfo(customer);
//            if (accountInfoList.size() == 0) {
//                throw new FlowException("Account doesn't exist");
//            }

            // Collect all of the required signatures from other Corda nodes using the CollectSignaturesFlow
            List<FlowSession> sessions = new ArrayList<>();
            FlowSession oldCustomerSession = initiateFlow(accountService.accountInfo(customer).get(0).getState().getData().getHost());
            FlowSession newBankSession = initiateFlow(newBank);
            FlowSession newCustomerSession = initiateFlow(accountService.accountInfo(newCustomer).get(0).getState().getData().getHost());
            sessions.add(oldCustomerSession);
            sessions.add(newBankSession);
            sessions.add(newCustomerSession);
            final SignedTransaction fullySignedTransaction = subFlow(new CollectSignaturesFlow(partiallySignedTransaction,
                    sessions, Collections.singleton(getOurIdentity().getOwningKey())));

            // Remove the old bank session because old bank node has already recorded the transaction.
            sessions.remove(oldCustomerSession);
            return subFlow(new FinalityFlow(fullySignedTransaction, sessions));
        }
    }


    @InitiatedBy(TransferFlow.InitiatorFlow.class)
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
                        ContractState output = stx.getTx().outputsOfType(AssetSavingState.class).get(0);
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

