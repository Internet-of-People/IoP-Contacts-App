package org.fermat.redtooth.core.interfaces;

import org.fermat.redtooth.profile_server.model.Profile;

import java.net.URI;
import java.util.List;

/**
 * Created by furszy on 5/24/17.
 */

public interface ContactService {


    boolean createProfile();

    boolean updateProfile();

    /** Profiles created by a single app */
    List<ServiceProfile> listOwnedProfiles();
    /** Profiles that are not mine and this app knowns them */
    List<ServiceProfile> listConnectedProfiles();

}
