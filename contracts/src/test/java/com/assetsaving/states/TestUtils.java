package com.assetsaving.states;

import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.CordaX500Name;
import net.corda.testing.core.TestIdentity;

import java.security.PublicKey;
import java.util.UUID;

public class TestUtils {

    public static TestIdentity PartyA = new TestIdentity(new CordaX500Name("PartyA", "UK", "US"));
    public static TestIdentity PartyB = new TestIdentity(new CordaX500Name("PartyB", "JP", "US"));
    public static TestIdentity PartyC = new TestIdentity(new CordaX500Name("PartyC", "CN", "US"));
    public static TestIdentity PartyD = new TestIdentity(new CordaX500Name("PartyD", "CN", "US"));


    //    public static AccountInfo customerAInfo =  new AccountInfo("CustomerA", PartyA.getParty(), new UniqueIdentifier());
//    public static AccountInfo customerBInfo =  new AccountInfo("CustomerB", PartyB.getParty(), new UniqueIdentifier());
//    public static AccountInfo customerCInfo =  new AccountInfo("CustomerB", PartyC.getParty(), new UniqueIdentifier());
    public static AnonymousParty AccountA = new AnonymousParty(PartyA.getPublicKey());
    public static AnonymousParty AccountB = new AnonymousParty(PartyB.getPublicKey());
    public static AnonymousParty AccountC = new AnonymousParty(PartyC.getPublicKey());
    public static AnonymousParty AccountD = new AnonymousParty(PartyD.getPublicKey());


}
