package world.libertaria.shared.library.global.service;

import android.app.Application;

/**
 * Created by furszy on 7/28/17.
 */

public class ConnectApp extends Application{


    public final String getAppPackage(){
        return getPackageName();
    }

}
