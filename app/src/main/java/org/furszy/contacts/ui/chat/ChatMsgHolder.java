package org.furszy.contacts.ui.chat;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.furszy.contacts.R;
import tech.furszy.ui.lib.base.adapter.BaseViewHolder;

/**
 * Created by furszy on 7/4/17.
 */

public class ChatMsgHolder extends BaseViewHolder {

    LinearLayout container_msg;
    TextView txt_message;
    TextView txt_time;

    protected ChatMsgHolder(View itemView, int holderType) {
        super(itemView, holderType);
        container_msg = (LinearLayout) itemView.findViewById(R.id.container_msg);
        txt_message = (TextView) itemView.findViewById(R.id.txt_message);
        txt_time = (TextView) itemView.findViewById(R.id.txt_time);
    }
}
