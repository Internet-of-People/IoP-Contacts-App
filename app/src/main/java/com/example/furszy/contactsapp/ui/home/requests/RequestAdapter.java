package com.example.furszy.contactsapp.ui.home.requests;

import android.app.Activity;
import android.graphics.BitmapFactory;
import android.view.View;

import com.example.furszy.contactsapp.R;
import com.example.furszy.contactsapp.adapter.BaseAdapter;
import com.example.furszy.contactsapp.adapter.FermatListItemListeners;

import org.fermat.redtooth.profile_server.ModuleRedtooth;
import org.fermat.redtooth.profile_server.ProfileInformation;
import org.fermat.redtooth.profiles_manager.PairingRequest;

/**
 * Created by mati on 03/03/17.
 */
public class RequestAdapter extends BaseAdapter<PairingRequest, RequestHolder> {

    private ModuleRedtooth module;
    private RequestListener requestListener;

    public interface RequestListener{

        void onAcceptRequest(PairingRequest pairingRequest);

        void onCancelRequest(PairingRequest pairingRequest);
    }

    public RequestAdapter(final Activity context, final ModuleRedtooth module,RequestListener requestListener) {
        super(context);
        this.module = module;
        this.requestListener = requestListener;
    }

    @Override
    protected RequestHolder createHolder(View itemView, int type) {
        return new RequestHolder(itemView, type);
    }

    @Override
    protected int getCardViewResource(int type) {
        return R.layout.request_row;
    }

    @Override
    protected void bindHolder(final RequestHolder holder, final PairingRequest data, int position) {
        holder.txt_name.setText(data.getSenderName());
        holder.btn_confirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (requestListener!=null)
                    requestListener.onAcceptRequest(data);
            }
        });
        holder.btn_cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (requestListener!=null)
                    requestListener.onCancelRequest(data);
            }
        });

    }
}
