package org.libertaria.world.profile_server.engine.app_services;

import org.libertaria.world.global.utils.SerializationUtils;

import java.io.IOException;
import java.io.Serializable;

/**
 * Created by furszy on 6/5/17.
 */

public class MessageWrapper implements Serializable {

    private BaseMsg msg;
    private String msgType;

    public MessageWrapper(BaseMsg msg, String msgType) {
        this.msg = msg;
        this.msgType = msgType;
    }

    public BaseMsg getMsg() {
        return msg;
    }

    public String getMsgType() {
        return msgType;
    }

    public byte[] encode() throws IOException {
        return SerializationUtils.serialize(this);
    }

    public static MessageWrapper decode(byte[] data) throws IOException, ClassNotFoundException {
        return SerializationUtils.deserialize(data,MessageWrapper.class);
    }
}
