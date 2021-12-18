package com.assetsaving.contracts;


import com.assetsaving.states.AssetSavingState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.identity.AbstractParty;
import net.corda.core.transactions.LedgerTransaction;

import java.security.PublicKey;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

public class AssetSavingContract implements Contract {
    public static final String ID = "com.template.contracts.AssetSavingContract";

    @Override
    public void verify(LedgerTransaction tx) {

        final CommandWithParties<Commands> command = requireSingleCommand(tx.getCommands(), Commands.class);
        final CommandData commandData = command.getValue();

        if (commandData instanceof AssetSavingContract.Commands.Issue) {
            requireThat(require -> {
                AssetSavingState assetSavingState = tx.outputsOfType(AssetSavingState.class).get(0);

                require.using("No inputs should be consumed when issuing an AssetSaving.",
                        tx.getInputStates().size() == 0);
                require.using("Only one output state should be created when issuing an AssetSaving.",
                        tx.getOutputStates().size() == 1);
                require.using("The Amount of the accumulation should be larger than 0.",
                        assetSavingState.getAccumulation().getQuantity() > 0);
                require.using("The start day should be later than today.",
                        assetSavingState.getStartDate().after(new Date()));

                List<PublicKey> signers = tx.getCommands().get(0).getSigners();
                HashSet<PublicKey> signersSet = new HashSet<>();
                for (PublicKey key : signers) {
                    signersSet.add(key);
                }

                List<AbstractParty> participants = tx.getOutputStates().get(0).getParticipants();
                HashSet<PublicKey> participantKeys = new HashSet<>();
                for (AbstractParty party : participants) {
                    participantKeys.add(party.getOwningKey());
                }

                require.using("Both bank and customer together only may sign AssetSaving issue transaction.",
                        signersSet.containsAll(participantKeys) && signersSet.size() == 2);

                return null;
            });
        } else if (commandData instanceof AssetSavingContract.Commands.Update) {
            requireThat(require -> {
                require.using("An AssetSaving update transaction should only consume one input state.",
                        tx.getInputStates().size() == 1);
                require.using("An AssetSaving update transaction should only create one output state.",
                        tx.getOutputStates().size() == 1);

                AssetSavingState outputState = tx.outputsOfType(AssetSavingState.class).get(0);
                AssetSavingState inputState = tx.inputsOfType(AssetSavingState.class).get(0);

                require.using("The Amount of the accumulation should be changed.",
                        outputState.getAccumulation().getQuantity() !=
                                inputState.getAccumulation().getQuantity());
                require.using("Other properties except accumulation must not be changed.",
                        inputState.getBank().equals(outputState.getBank()) &&
                        inputState.getCustomer().equals(outputState.getCustomer()) &&
                        inputState.getStartDate().equals(outputState.getStartDate()) &&
                        inputState.getLinearId().equals(outputState.getLinearId()));

                List<PublicKey> signers = tx.getCommands().get(0).getSigners();
                HashSet<PublicKey> signersSet = new HashSet<>();
                for (PublicKey key : signers) {
                    signersSet.add(key);
                }

                List<AbstractParty> participants = tx.getOutputStates().get(0).getParticipants();
                HashSet<PublicKey> participantKeys = new HashSet<>();
                for (AbstractParty party : participants) {
                    participantKeys.add(party.getOwningKey());
                }

                require.using("Both bank and customer together only may sign AssetSaving update transaction.",
                        signersSet.containsAll(participantKeys) && signersSet.size() == 2);


                return null;
            });
        } else if (commandData instanceof AssetSavingContract.Commands.Transfer) {
            requireThat(require -> {
                require.using("An AssetSaving transfer transaction should only consume one input state.",
                        tx.getInputStates().size() == 1);
                require.using("An AssetSaving transfer transaction should only create one output state.",
                        tx.getOutputStates().size() == 1);

                AssetSavingState outputState = tx.outputsOfType(AssetSavingState.class).get(0);
                AssetSavingState inputState = tx.inputsOfType(AssetSavingState.class).get(0);

                require.using("The bank of the input state should be different from the output state.",
                        !outputState.getBank().equals(inputState.getBank()));
                require.using("The customer of the input state should be different from the output state.",
                        !outputState.getCustomer().equals(inputState.getCustomer()));
                require.using("The start day should be later than today.",
                        outputState.getStartDate().after(new Date()));
                require.using("The linearId must not be changed.",
                        inputState.getLinearId().equals(outputState.getLinearId()));

                Set<PublicKey> listOfParticipantPublicKeys = inputState.getParticipants()
                        .stream().map(AbstractParty::getOwningKey).collect(Collectors.toSet());
                listOfParticipantPublicKeys.add(outputState.getBank().getOwningKey());
                listOfParticipantPublicKeys.add(outputState.getCustomer().getOwningKey());

                List<PublicKey> arrayOfSigners = tx.getCommands().get(0).getSigners();
                Set<PublicKey> setOfSigners = new HashSet<PublicKey>(arrayOfSigners);
                require.using("The old and new customer account and, old and new bank must sign an AssetSaving transfer transaction",
                        setOfSigners.equals(listOfParticipantPublicKeys) && setOfSigners.size() == 4);

                return null;
            });
        } else if (commandData instanceof AssetSavingContract.Commands.Accumulate) {
            // Implement if you can afford it.
        } else if (commandData instanceof AssetSavingContract.Commands.Cancel) {
            requireThat(require -> {
                require.using("Only one input state should be consumed when cancel an AssetSaving.",
                        tx.getInputStates().size() == 1);
                require.using("No output state should be created when cancel an AssetSaving.",
                        tx.getOutputStates().size() == 0);

                List<PublicKey> signers = tx.getCommands().get(0).getSigners();
                HashSet<PublicKey> signersSet = new HashSet<>();
                for (PublicKey key : signers) {
                    signersSet.add(key);
                }

                AssetSavingState inputState = tx.inputsOfType(AssetSavingState.class).get(0);

                Set<PublicKey> listOfParticipantPublicKeys = inputState.getParticipants()
                        .stream().map(AbstractParty::getOwningKey).collect(Collectors.toSet());

                List<PublicKey> arrayOfSigners = tx.getCommands().get(0).getSigners();
                Set<PublicKey> setOfSigners = new HashSet<PublicKey>(arrayOfSigners);


//                List<AbstractParty> participants = tx.getOutputStates().get(0).getParticipants();
//                HashSet<PublicKey> participantKeys = new HashSet<>();
//                for (AbstractParty party : participants) {
//                    participantKeys.add(party.getOwningKey());
//                }

                require.using("Both bank and customer together only may sign the AssetSaving cancel transaction.",
                        setOfSigners.equals(listOfParticipantPublicKeys) && signersSet.size() == 2);

                return null;
            });
        }
    }

    public interface Commands extends CommandData {
        class Issue implements Commands {
        }

        class Update implements Commands {
        }

        class Transfer implements Commands {
        }

        class Accumulate implements Commands {
        }

        class Cancel implements Commands {
        }
    }
}
