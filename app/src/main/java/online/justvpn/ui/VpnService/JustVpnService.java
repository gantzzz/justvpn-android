package online.justvpn.ui.VpnService;

import android.net.VpnService;
import android.os.ParcelFileDescriptor;

public class JustVpnService extends VpnService {
    private Builder mBuilder;
    private ParcelFileDescriptor mInterface;
}
