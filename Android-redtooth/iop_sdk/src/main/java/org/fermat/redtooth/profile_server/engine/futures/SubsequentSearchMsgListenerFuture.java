package org.fermat.redtooth.profile_server.engine.futures;

import java.util.List;

import org.fermat.redtooth.profile_server.engine.SearchProfilesQuery;
import org.fermat.redtooth.profile_server.engine.listeners.ProfSerPartSearchListener;
import org.fermat.redtooth.profile_server.protocol.IopProfileServer;

/**
 * Created by mati on 31/03/17.
 */

public class SubsequentSearchMsgListenerFuture<O extends List<IopProfileServer.IdentityNetworkProfileInformation>> extends BaseMsgFuture<O> implements ProfSerPartSearchListener<O> {

    private SearchProfilesQuery searchProfilesQuery;

    public SubsequentSearchMsgListenerFuture(SearchProfilesQuery searchProfilesQuery) {
        this.searchProfilesQuery = searchProfilesQuery;
    }

    @Override
    public void onMessageReceive(int messageId, O message, int recordIndex, int recordCount) {
        this.messageId = messageId;
        this.searchProfilesQuery.setLastRecordIndex(recordIndex);
        this.searchProfilesQuery.setLastRecordCount(recordCount);
        this.searchProfilesQuery.addListToChache(recordIndex,message);
        queue.offer(message);
    }

    @Override
    public void onMsgFail(int messageId, int statusValue, String details) {
        this.status = statusValue;
        this.statusDetail = details;
        queue.offer(null);
    }
}
