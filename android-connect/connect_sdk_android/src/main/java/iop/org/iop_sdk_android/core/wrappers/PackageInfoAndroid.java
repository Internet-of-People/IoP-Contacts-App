package iop.org.iop_sdk_android.core.wrappers;

import android.content.pm.PackageInfo;

import org.libertaria.world.global.PackageInformation;

/**
 * Created by mati on 26/12/16.
 */

public class PackageInfoAndroid implements PackageInformation {

    private PackageInfo packageInfo;

    public PackageInfoAndroid(PackageInfo packageInfo) {
        this.packageInfo = packageInfo;
    }

    @Override
    public String getVersionName() {
        return packageInfo.versionName;
    }

    @Override
    public Object getPackageInfo() {
        return packageInfo;
    }
}
