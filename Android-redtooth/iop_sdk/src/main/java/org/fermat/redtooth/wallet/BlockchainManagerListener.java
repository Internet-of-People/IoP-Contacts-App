package org.fermat.redtooth.wallet;

import org.bitcoinj.core.PeerGroup;
import org.fermat.redtooth.wallet.utils.BlockchainState;

import java.util.Set;

/**
 * Created by mati on 19/12/16.
 */
public interface BlockchainManagerListener {

    void peerGroupInitialized(PeerGroup peerGroup);

    void onBlockchainOff(Set<BlockchainState.Impediment> impediments);

    void checkStart();

    void checkEnd();

}
