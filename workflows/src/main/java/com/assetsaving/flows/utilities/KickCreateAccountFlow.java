package com.assetsaving.flows.utilities;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.utilities.UntrustworthyData;

import java.util.Arrays;

public class KickCreateAccountFlow {
    @InitiatingFlow
    @StartableByRPC
    public static class InitiatorFlow extends FlowLogic<String> {

        private final Party destinationParty;
        private final String accountName;

        public InitiatorFlow(Party destinationParty, String accountName) {
            this.destinationParty = destinationParty;
            this.accountName = accountName;
        }

        @Override
        @Suspendable
        public String call() throws FlowException {
            // We need to have been informed about this possibly anonymous identity ahead of time.
            // Create the session.
            final FlowSession session = initiateFlow(destinationParty);
            // Send a message.
            UntrustworthyData receive = session.sendAndReceive(String.class, accountName);
            String result = (String) receive.unwrap(it -> it);
            return receive.toString();
        }
    }

    @InitiatedBy(KickCreateAccountFlow.InitiatorFlow.class)
    public static class ResponderFlow extends FlowLogic<String> {

        private final FlowSession flowSession;

        public ResponderFlow(FlowSession flowSession) {
            this.flowSession = flowSession;
        }

        @Override
        @Suspendable
        public String call() throws FlowException {
            UntrustworthyData receive = flowSession.receive(String.class);
            String name = (String) receive.unwrap(it -> it);
            // Create a new account.
            subFlow(new CreateAccount(name));
            // Share the account with the counter party.
            subFlow(new ShareAccountFlow(name, Arrays.asList(flowSession.getCounterparty())));
            flowSession.send(name + " was created and shared!");
            return name + " is successfully created and shared!";
        }
    }
}
