package com.assetsaving.flows.utilities;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.UtilitiesKt;
import com.assetsaving.states.AssetSavingState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.node.services.vault.QueryCriteria;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@StartableByRPC
public class QuerybyAccount extends FlowLogic<List<AssetSavingStateModel>> {

    private final String name;
    public QuerybyAccount(String name) {
        this.name = name;
    }

    @Override
    @Suspendable
    public List<AssetSavingStateModel> call() throws FlowException {
        AccountInfo myAccount = UtilitiesKt.getAccountService(this).accountInfo(name).get(0).getState().getData();
        UUID id = myAccount.getIdentifier().getId();
        QueryCriteria.VaultQueryCriteria criteria = new QueryCriteria.VaultQueryCriteria().withExternalIds(Arrays.asList(id));

        List<StateAndRef<AssetSavingState>> AssetSavingStateAndRefs =  getServiceHub().getVaultService().queryBy(AssetSavingState.class,criteria).getStates();

        if(AssetSavingStateAndRefs.size() == 0){
            return Collections.emptyList();
        }
        return AssetSavingStateAndRefs.stream().map(assetSavingStateAndRefs -> {
            AssetSavingState state = assetSavingStateAndRefs.getState().getData();
            String bank = state.getBank().toString();
            String customer = UtilitiesKt.getAccountService(this)
                    .accountInfo(state.getCustomer().getOwningKey()).getState().getData().getName();
            return new AssetSavingStateModel(bank, customer);
        }).collect(Collectors.toList());
    }
}
