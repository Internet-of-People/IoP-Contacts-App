package org.fermat.redtooth.core;

import org.fermat.redtooth.core.services.DefaultServices;
import org.fermat.redtooth.core.services.pairing.PairingAppService;
import org.fermat.redtooth.core.services.pairing.PairingMsg;
import org.fermat.redtooth.core.services.pairing.PairingMsgTypes;
import org.fermat.redtooth.crypto.CryptoBytes;
import org.fermat.redtooth.crypto.CryptoWrapper;
import org.fermat.redtooth.global.DeviceLocation;
import org.fermat.redtooth.locnet.Explorer;
import org.fermat.redtooth.locnet.NodeInfo;
import org.fermat.redtooth.profile_server.CantConnectException;
import org.fermat.redtooth.profile_server.CantSendMessageException;
import org.fermat.redtooth.profile_server.ProfileInformation;
import org.fermat.redtooth.profile_server.ProfileServerConfigurations;
import org.fermat.redtooth.profile_server.SslContextFactory;
import org.fermat.redtooth.profile_server.engine.app_services.CallProfileAppService;
import org.fermat.redtooth.profile_server.engine.app_services.PairingListener;
import org.fermat.redtooth.profile_server.engine.futures.ConnectionFuture;
import org.fermat.redtooth.profile_server.engine.listeners.ConnectionListener;
import org.fermat.redtooth.profile_server.engine.futures.BaseMsgFuture;
import org.fermat.redtooth.profile_server.engine.futures.MsgListenerFuture;
import org.fermat.redtooth.profile_server.engine.listeners.ProfSerMsgListener;
import org.fermat.redtooth.profile_server.imp.ProfileInformationImp;
import org.fermat.redtooth.profile_server.model.KeyEd25519;
import org.fermat.redtooth.profile_server.model.ProfServerData;
import org.fermat.redtooth.profile_server.model.Profile;
import org.fermat.redtooth.profile_server.protocol.IopProfileServer;
import org.fermat.redtooth.profiles_manager.PairingRequest;
import org.fermat.redtooth.profiles_manager.PairingRequestsManager;
import org.fermat.redtooth.profiles_manager.ProfilesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.FutureTask;

/**
 * Created by mati on 17/05/17.
 * todo: clase encargada de crear perfiles, agregar aplication services y hablar con las capas superiores.
 */

public class IoPConnect implements ConnectionListener {

    private final Logger logger = LoggerFactory.getLogger(IoPConnect.class);

    /** Map of device profiles pubKey connected to the home PS, profile public key -> host PS manager*/
    private ConcurrentMap<String,IoPProfileConnection> managers;
    /** Map of device profiles connected to remote PS */
    private ConcurrentMap<PsKey,IoPProfileConnection> remoteManagers = new ConcurrentHashMap<>();
    /** Enviroment context */
    private IoPConnectContext context;
    /** Profiles manager db */
    private ProfilesManager profilesManager;
    /** Pairing request manager db  */
    private PairingRequestsManager pairingRequestsManager;
    /** Gps */
    private DeviceLocation deviceLocation;
    /** Crypto platform implementation */
    private CryptoWrapper cryptoWrapper;
    /** Socket factory */
    private SslContextFactory sslContextFactory;

    private class PsKey{

        private String deviceProfPubKey;
        private String psHost;

        public PsKey(String deviceProfPubKey, String psHost) {
            this.deviceProfPubKey = deviceProfPubKey;
            this.psHost = psHost;
        }

        public String getDeviceProfPubKey() {
            return deviceProfPubKey;
        }

        public String getPsHost() {
            return psHost;
        }
    }

    public IoPConnect(IoPConnectContext contextWrapper, CryptoWrapper cryptoWrapper, SslContextFactory sslContextFactory, ProfilesManager profilesManager, PairingRequestsManager pairingRequestsManager,DeviceLocation deviceLocation) {
        this.context = contextWrapper;
        this.cryptoWrapper = cryptoWrapper;
        this.sslContextFactory = sslContextFactory;
        this.managers = new ConcurrentHashMap<>();
        this.profilesManager = profilesManager;
        this.pairingRequestsManager = pairingRequestsManager;
        this.deviceLocation = deviceLocation;
    }


    @Override
    public void onPortsReceived(String psHost, int nonClPort, int clPort, int appSerPort) {
        ProfileServerConfigurations profileServerConfigurations = createEmptyProfileServerConf();
        // todo: implement a db for profile servers..
        // But for now i'm lazy.. save this in my own profile server
        profileServerConfigurations.setMainPfClPort(clPort);
        profileServerConfigurations.setMainPsNonClPort(nonClPort);
        profileServerConfigurations.setMainAppServicePort(appSerPort);
    }

    @Override
    public void onHostingPlanReceived(String host, IopProfileServer.HostingPlanContract contract) {
        ProfileServerConfigurations profileServerConfigurations = createEmptyProfileServerConf();
        // todo: implement this for multi profiles..
        // for now i don't have to worry, i just have one profile.
        profileServerConfigurations.setProfileRegistered(host,CryptoBytes.toHexString(contract.getIdentityPublicKey().toByteArray()));
    }

    @Override
    public void onNonClConnectionStablished(String host) {

    }


    /**
     * Create a profile inside the redtooth
     *
     * @param profileOwnerChallenge -> the owner of the profile must sign his messages
     * @param name
     * @param type
     * @param extraData
     * @param secretPassword -> encription password for the profile keys
     * @return profile pubKey
     */
    public Profile createProfile(byte[] profileOwnerChallenge,String name,String type,byte[] img,String extraData,String secretPassword){
        byte[] version = new byte[]{0,0,1};
        ProfileServerConfigurations profileServerConfigurations = createEmptyProfileServerConf();
        KeyEd25519 keyEd25519 = profileServerConfigurations.createNewUserKeys();
        Profile profile = new Profile(version, name,type,keyEd25519);
        profile.setExtraData(extraData);
        profile.setImg(img);
        // save
        profileServerConfigurations.saveUserKeys(profile.getKey());
        profileServerConfigurations.setIsCreated(true);
        // save profile
        profileServerConfigurations.saveProfile(profile);
        // todo: return profile connection pk
        return profile;
    }

    public void backupProfile(long profId,String backupDir){
        //Profile profile = profilesManager.getProfile(profId);
        // todo: backup the profile on an external dir file.
    }

    public void connectProfile(String profilePublicKey, PairingListener pairingListener, byte[] ownerChallenge,ConnectionFuture future) throws Exception {
        if (managers.containsKey(profilePublicKey))throw new IllegalArgumentException("Profile connection already initialized");
        ProfileServerConfigurations profileServerConfigurations = createEmptyProfileServerConf();
        ProfServerData profServerData = null;
        if(profileServerConfigurations.getMainProfileServerContract()==null){
            // search in LOC for a profile server or use a trusted one from the user.
            // todo: here i have to do the LOC Network flow.
            // Sync explore profile servers around Argentina
            if (false){
                Explorer explorer = new Explorer( NodeInfo.ServiceType.Profile, deviceLocation.getDeviceLocation(), 10000, 10 );
                FutureTask< List<NodeInfo> > task = new FutureTask<>(explorer);
                task.run();
                List<NodeInfo> resultNodes = task.get();
                // chose the first one - closest
                if (!resultNodes.isEmpty()) {
                    NodeInfo selectedNode = resultNodes.get(0);
                    profServerData = new ProfServerData(
                            selectedNode.getNodeId(),
                            selectedNode.getContact().getAddress().getHostAddress(),
                            selectedNode.getContact().getPort(),
                            selectedNode.getLocation().getLatitude(),
                            selectedNode.getLocation().getLongitude()
                    );
                }
            }else {
                profServerData = profileServerConfigurations.getMainProfileServer();
            }
        }
        KeyEd25519 keyEd25519 = (KeyEd25519) profileServerConfigurations.getUserKeys();
        if (keyEd25519==null) throw new IllegalStateException("no pubkey saved");
        future.setProfServerData(profServerData);
        addConnection(profileServerConfigurations,profServerData,keyEd25519,pairingListener).init(future,this);
    }

    public int updateProfile(Profile profile, ProfSerMsgListener msgListener) throws Exception {
        return getProfileConnection(profile.getHexPublicKey()).updateProfile(profile.getVersion(),profile.getName(),profile.getImg(),profile.getLatitude(),profile.getLongitude(),profile.getExtraData(),msgListener);
    }

    /**
     *
     * Add PS home connection
     *
     * @param profileServerConfigurations
     * @param profConn
     * @param profKey
     * @param pairingListener
     * @return
     */
    private IoPProfileConnection addConnection(ProfileServerConfigurations profileServerConfigurations,ProfServerData profConn, KeyEd25519 profKey, PairingListener pairingListener){
        // profile connection
        IoPProfileConnection ioPProfileConnection = new IoPProfileConnection(
                context,
                initClientData(profileServerConfigurations,pairingListener),
                profConn,
                cryptoWrapper,
                sslContextFactory,
                deviceLocation);
        // map the profile connection with his public key
        managers.put(profKey.getPublicKeyHex(), ioPProfileConnection);
        return ioPProfileConnection;
    }

    /**
     * Add PS guest connection looking for a remote profile.
     *
     * @param profConn
     * @param deviceProfile
     * @param psKey
     * @return
     */
    private IoPProfileConnection addConnection(ProfServerData profConn, Profile deviceProfile ,PsKey psKey){
        // profile connection
        IoPProfileConnection ioPProfileConnection = new IoPProfileConnection(
                context,
                deviceProfile,
                profConn,
                cryptoWrapper,
                sslContextFactory,
                deviceLocation);
        // map the profile connection with his public key
        remoteManagers.put(psKey, ioPProfileConnection);
        return ioPProfileConnection;
    }

    private Profile initClientData(ProfileServerConfigurations profileServerConfigurations, PairingListener pairingListener) {
        //todo: esto lo tengo que hacer cuando guarde la privkey encriptada.., por ahora lo dejo asI. Este es el profile que va a crear el usuario, está acá de ejemplo.
        Profile profile = null;
        if (profileServerConfigurations.isIdentityCreated()) {
            // load profileCache
            profile = profileServerConfigurations.getProfile();
        } else {
            // create and save
            KeyEd25519 keyEd25519 = profileServerConfigurations.createUserKeys();
            profile = new Profile(profileServerConfigurations.getProfileVersion(), profileServerConfigurations.getUsername(), profileServerConfigurations.getProfileType(),keyEd25519);
            profile.setImg(profileServerConfigurations.getUserImage());
            // save
            profileServerConfigurations.saveUserKeys(profile.getKey());
        }
        // pairing default
        if(profileServerConfigurations.isPairingEnable()){
            if (pairingListener==null) throw new IllegalArgumentException("Pairing listener cannot be null if configurations pairing is enabled");
            profile.addApplicationService(new PairingAppService(profile,pairingRequestsManager,profilesManager,pairingListener));
        }
        return profile;
    }

    /**
     * Search based on CAN, could be LOC and Profile server.
     *
     * @param requeteerPubKey
     * @param profPubKey
     * @param future
     * @throws CantConnectException
     * @throws CantSendMessageException
     */
    public void searchAndGetProfile(final String requeteerPubKey, String profPubKey, final ProfSerMsgListener<ProfileInformation> future) throws CantConnectException, CantSendMessageException {
        if (!managers.containsKey(requeteerPubKey)) throw new IllegalStateException("Profile connection not established");
        ProfileInformation info = profilesManager.getProfile(requeteerPubKey,profPubKey);
        if (info!=null){
            //todo: add TTL and expiration -> info.getLastUpdateTime().
            // if it's not valid go to CAN.
            future.onMessageReceive(0,info);
        }else {
            // CAN FLOW


            //
            MsgListenerFuture<IopProfileServer.GetProfileInformationResponse> getFuture = new MsgListenerFuture<>();
            getFuture.setListener(new BaseMsgFuture.Listener<IopProfileServer.GetProfileInformationResponse>() {
                @Override
                public void onAction(int messageId, IopProfileServer.GetProfileInformationResponse message) {
                    IopProfileServer.ProfileInformation signedProfile = message.getSignedProfile().getProfile();
                    ProfileInformationImp profileInformation = new ProfileInformationImp();
                    profileInformation.setVersion(signedProfile.getVersion().toByteArray());
                    profileInformation.setPubKey(signedProfile.getPublicKey().toByteArray());
                    profileInformation.setName(signedProfile.getName());
                    profileInformation.setType(signedProfile.getType());
                    profileInformation.setImgHash(signedProfile.getProfileImageHash().toByteArray());
                    profileInformation.setTumbnailImgHash(signedProfile.getThumbnailImageHash().toByteArray());
                    profileInformation.setLatitude(signedProfile.getLatitude());
                    profileInformation.setLongitude(signedProfile.getLongitude());
                    profileInformation.setExtraData(signedProfile.getExtraData());
                    profileInformation.setIsOnline(message.getIsOnline());
                    profileInformation.setUpdateTimestamp(System.currentTimeMillis());

                    for (int i = 0; i < message.getApplicationServicesCount(); i++) {
                        profileInformation.addAppService(message.getApplicationServices(i));
                    }
                    // save unknown profile
                    profilesManager.saveProfile(requeteerPubKey,profileInformation);

                    future.onMessageReceive(messageId, profileInformation);
                }

                @Override
                public void onFail(int messageId, int status, String statusDetail) {
                    future.onMsgFail(messageId, status, statusDetail);
                }
            });
            managers.get(requeteerPubKey).getProfileInformation(profPubKey, true, false, true, getFuture);
        }
    }

    /**
     * Send a request pair notification to a remote profile
     *
     * @param pairingRequest
     * @param listener -> returns the pairing request id
     */
    public void requestPairingProfile(final PairingRequest pairingRequest, final ProfSerMsgListener<Integer> listener) throws Exception {
        logger.info("requestPairingProfile, remote: " + pairingRequest.getRemotePubKey());
        // save request
        final int pairingRequestId = pairingRequestsManager.savePairingRequest(pairingRequest);
        // Connection
        final IoPProfileConnection connection = getOrStablishConnection(pairingRequest.getRemotePsHost(),pairingRequest.getSenderPubKey(),pairingRequest.getSenderPsHost());
        // first the call
        MsgListenerFuture<CallProfileAppService> callListener = new MsgListenerFuture();
        callListener.setListener(new BaseMsgFuture.Listener<CallProfileAppService>() {
            @Override
            public void onAction(int messageId, final CallProfileAppService call) {
                try {
                    if (call.isStablished()) {
                        logger.info("call establish, remote: " + call.getRemotePubKey());
                        // now send the pairing message
                        MsgListenerFuture<Boolean> pairingMsgFuture = new MsgListenerFuture();
                        pairingMsgFuture.setListener(new BaseMsgFuture.Listener<Boolean>() {
                            @Override
                            public void onAction(int messageId, Boolean res) {
                                logger.info("pairing msg sent, remote: " + call.getRemotePubKey());
                                listener.onMessageReceive(messageId, pairingRequestId);
                            }

                            @Override
                            public void onFail(int messageId, int status, String statusDetail) {
                                logger.info("pairing msg fail, remote: " + call.getRemotePubKey());
                                listener.onMsgFail(messageId, status, statusDetail);
                            }
                        });
                        PairingMsg pairingMsg = new PairingMsg(pairingRequest.getSenderName(),pairingRequest.getSenderPsHost());
                        call.sendMsg(pairingMsg, pairingMsgFuture);
                    } else {
                        logger.info("call fail with status: " + call.getStatus() + ", error: " + call.getErrorStatus());
                        listener.onMsgFail(messageId, 0, call.getStatus().toString() + " " + call.getErrorStatus());
                    }

                } catch (CantSendMessageException e) {
                    e.printStackTrace();
                    listener.onMsgFail(messageId, 400, e.getMessage());
                } catch (CantConnectException e) {
                    e.printStackTrace();
                    listener.onMsgFail(messageId, 400, e.getMessage());
                } catch (Exception e) {
                    e.printStackTrace();
                    listener.onMsgFail(messageId, 400, e.getMessage());
                }
            }

            @Override
            public void onFail(int messageId, int status, String statusDetail) {
                logger.info("call fail, remote: " + statusDetail);
                listener.onMsgFail(messageId, status, statusDetail);
            }
        });
        connection.callProfileAppService(pairingRequest.getRemotePubKey(), DefaultServices.PROFILE_PAIRING.getName(), false, false, callListener);
    }

    /**
     * Send a pair acceptance
     *
     * @param pairingRequest
     */
    public void acceptPairingRequest(PairingRequest pairingRequest) throws Exception {
        // Remember that here the local device is the pairingRequest.getSender()
        String remotePubKeyHex =  pairingRequest.getSenderPubKey();
        String localPubKeyHex = pairingRequest.getRemotePubKey();
        logger.info("acceptPairingRequest, remote: " + remotePubKeyHex);
        // update in db the acceptance first
        // todo: here i have to add the pair request db and tick this as done. and save the profile with paired true.
        profilesManager.updatePaired(
                localPubKeyHex,
                remotePubKeyHex,
                ProfileInformationImp.PairStatus.PAIRED);
        pairingRequestsManager.updateStatus(
                remotePubKeyHex,
                localPubKeyHex,
                PairingMsgTypes.PAIR_ACCEPT, ProfileInformationImp.PairStatus.PAIRED);
        // requestsDbManager.removeRequest(remotePubKeyHex);

        // Notify the other side if it's connected.
        // first check if i have a connection with the server hosting the pairing sender
        // tengo que ver si el remote profile tiene como home host la conexion principal de el sender profile al PS
        // si no la tiene abro otra conexion.
        final IoPProfileConnection connection = getOrStablishConnection(pairingRequest.getRemotePsHost(),pairingRequest.getRemotePubKey(),pairingRequest.getSenderPsHost());
        final CallProfileAppService call = connection.getActiveAppCallService(remotePubKeyHex);
        final MsgListenerFuture<Boolean> future = new MsgListenerFuture<>();
        // Add listener -> todo: add future listener and save acceptPairing sent
        future.setListener(new BaseMsgFuture.Listener<Boolean>() {
            @Override
            public void onAction(int messageId, Boolean object) {
                try {
                    logger.info("PairAccept sent");
                    if (call!=null)
                        call.dispose();
                    else
                        logger.warn("call null trying to dispose pairing app service. Check this");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFail(int messageId, int status, String statusDetail) {
                logger.info("PairAccept fail, "+status+", detail: "+statusDetail);
                //todo: schedule and re try
                try {
                    if (call!=null)
                        call.dispose();
                    else
                        logger.warn("call null trying to dispose pairing app service. Check this");
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });
        if (call != null) {
            call.sendMsg(PairingMsgTypes.PAIR_ACCEPT.getType(), future);
        }else {
            MsgListenerFuture<CallProfileAppService> callFuture = new MsgListenerFuture<>();
            callFuture.setListener(new BaseMsgFuture.Listener<CallProfileAppService>() {
                @Override
                public void onAction(int messageId, CallProfileAppService call) {
                    try {
                        call.sendMsg(PairingMsgTypes.PAIR_ACCEPT.getType(), future);
                    } catch (Exception e) {
                        logger.error("call sendMsg error",e);
                        future.onMsgFail(messageId,400,e.getMessage());
                    }
                }

                @Override
                public void onFail(int messageId, int status, String statusDetail) {
                    logger.error("call sendMsg fail",statusDetail);
                    future.onMsgFail(messageId,status,statusDetail);
                }
            });
            connection.callProfileAppService(remotePubKeyHex,DefaultServices.PROFILE_PAIRING.getName(),true,false,callFuture);
        }
    }

    public void cancelPairingRequest(PairingRequest pairingRequest) {
        pairingRequestsManager.delete(pairingRequest.getId());
    }


    private ProfileServerConfigurations createEmptyProfileServerConf(){
        return context.createProfSerConfig();
    }

    private IoPProfileConnection getProfileConnection(String profPubKey) throws org.fermat.redtooth.core.exceptions.ProfileNotConectedException {
        if (!managers.containsKey(profPubKey)) throw new org.fermat.redtooth.core.exceptions.ProfileNotConectedException("Profile connection not established");
        return managers.get(profPubKey);
    }

    /**
     *
     * If the remote ps host is the same as the home node return the main connection if not create another one to the remote server.
     *
     * @param localPsHost
     * @param localProfPubKey
     * @param remotePsHost
     * @return
     * @throws Exception
     */
    private IoPProfileConnection getOrStablishConnection(String localPsHost,String localProfPubKey,String remotePsHost) throws Exception {
        IoPProfileConnection connection = null;
        if (localPsHost.equals(remotePsHost)) {
            connection = getProfileConnection(localProfPubKey);
        }else {
            PsKey psKey = new PsKey(localProfPubKey,remotePsHost);
            if(remoteManagers.containsKey(psKey)){
                connection = remoteManagers.get(psKey);
            }else {
                ProfServerData profServerData = new ProfServerData(remotePsHost);
                Profile profile = createEmptyProfileServerConf().getProfile();
                connection = addConnection(profServerData,profile,psKey);
                connection.init(this);
            }
        }
        return connection;
    }

    /**
     * Load a profile server configuration from one profile
     * @param profPk
     * @return
     */
    private ProfileServerConfigurations loadProfileServerConf(String profPk){
        return null;
    }


    public List<ProfileInformation> getKnownProfiles(String pubKey){
        return profilesManager.listConnectedProfiles(pubKey);
    }

    public ProfileInformation getKnownProfile(String contactOwnerPubKey,String pubKey) {
        return profilesManager.getProfile(contactOwnerPubKey,pubKey);
    }

    public void stop() {
        for (Map.Entry<String, IoPProfileConnection> stringRedtoothProfileConnectionEntry : managers.entrySet()) {
            try {
                stringRedtoothProfileConnectionEntry.getValue().stop();
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

}
