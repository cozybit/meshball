package com.samsung.meshball.utils;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.io.BufferedWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This class ...
 */
public class WifiUtils
{
    private static final String TAG = WifiUtils.class.getName();

    public static final int WIFI_AP_STATE_FAILED = 4;
    public static final int WIFI_AP_STATE_DISABLED[] = { 1, 11 };
    public static final int WIFI_AP_STATE_ENABLED[] = { 3, 13 };
    
    private static final String INT_PRIVATE_KEY = "private_key";
    private static final String INT_PHASE2 = "phase2";
    private static final String INT_PASSWORD = "password";
    private static final String INT_IDENTITY = "identity";
    private static final String INT_EAP = "eap";
    private static final String INT_CLIENT_CERT = "client_cert";
    private static final String INT_CA_CERT = "ca_cert";
    private static final String INT_ANONYMOUS_IDENTITY = "anonymous_identity";

    public final static String INT_ENTERPRISEFIELD_NAME = "android.net.wifi.WifiConfiguration$EnterpriseField";

    //Wifi related variables
    private WifiManager wifiManager;
    private Map<String, ScanMap> bssidMap = new HashMap<String, ScanMap>();

    /**
     * This inner class maps the ScanResults of the available access points to the known
     * WifiConfigurations where the configuration could be null if not previously associated with.
     */
    public class ScanMap
    {
        ScanResult scan;
        WifiConfiguration config;

        public ScanMap(WifiConfiguration config, ScanResult scan)
        {
            this.config = config;
            this.scan = scan;
        }

        public WifiConfiguration getConfig()
        {
            return config;
        }

        public ScanResult getScan()
        {
            return scan;
        }

        @Override
        public boolean equals(Object o)
        {
            if(this == o) {
                return true;
            }
            if(o == null || getClass() != o.getClass()) {
                return false;
            }

            ScanMap scanMap = (ScanMap) o;

            if(config != null ? !config.equals(scanMap.config) : scanMap.config != null) {
                return false;
            }
            if(scan != null ? !scan.equals(scanMap.scan) : scanMap.scan != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = scan != null ? scan.hashCode() : 0;
            result = 31 * result + (config != null ? config.hashCode() : 0);
            return result;
        }
    }

    public WifiUtils(Context context)
    {
        try {
            wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            Log.i(TAG, "BSSID = %s, SSID = %s", wifiManager.getConnectionInfo().getBSSID(), wifiManager.getConnectionInfo().getSSID());
        }
        catch(Throwable e) {
            Log.e(TAG, e, "Caught exception: %s", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Returns the encapsulated WifiManager
     * @return WifiManager
     */
    public WifiManager getWifiManager()
    {
        return wifiManager;
    }

    public void startScan()
    {
        Log.mark( TAG );
        wifiManager.startScan();
    }

    /**
     * This method will take the scan results and correlate them with the known network configurations
     * that are currently visible.  The product is a map of BSSID to WifiConfiguration, or NULL.
     *
     * @param scanResults List of ScanResults returned from a scan
     */
    public void setScanResults(List<ScanResult> scanResults)
    {
        List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
        for(ScanResult result : scanResults) {
            String ssid = "\"" + result.SSID + "\"";

            WifiConfiguration cfg = null;
            for(WifiConfiguration config : configs) {
                if ( ssid.equals(config.SSID) ) {
                    cfg = config;
                    break;
                }
            }
            bssidMap.put(result.BSSID, new ScanMap( cfg, result ));
        }
    }

    /**
     * This method will return the WifiConfiguration of a given SSID.  Note, if the client has never join the
     * SSID, then there is no configuration.
     *
     * @param ssid The String SSID
     * @return the WifiConfiguration
     */
    public WifiConfiguration getWifiConfiguration(String ssid)
    {
        String s = "\"" + ssid + "\"";

        List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
        for(WifiConfiguration config : configs) {
            if ( s.equals(config.SSID) ) {
                return config;
            }
        }
        return null;
    }

    /**
     * Returns the WifiConfiguration by BSSID.
     *
     * @param bssid String BSSID to lookup
     * @return ScanMap of BSSID or null
     */
    public ScanMap getMappingByBSSID(String bssid)
    {
        return bssidMap.get(bssid.toLowerCase());
    }

    /**
     * This invokes the hidden method on WifiManager class by the same name and signature through
     * reflection.  It is very important to understand that his is invoking a private API method that
     * might change.
     * <p/>
     * <b><u>WARNING!!!!:</u></b> It is also very important to understand the implication of passing in a
     * WifiConfiguration versus null.  On some devices, such as the HTC EVO, passing in a WifiConfiguration
     * results in a corrupt hot spot.
     * <p/>
     * To enable the existing AP configuration, which will either be factory default, or explicitly
     * set by the user, pass in null.
     *
     * @param config the WifiConfiguration to set.  Pass in null to enable existing config.
     * @param enabled boolean true or false
     * @return true or false if the AP was enabled/disabled
     */
    public boolean setWifiApEnabled(WifiConfiguration config, boolean enabled)
    {
        try {
            if(enabled) {
                wifiManager.setWifiEnabled(false);
            }

            Method method = wifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            if ( method != null ) {
                return (Boolean) method.invoke(wifiManager, config, enabled);
            }
        }
        catch(Exception e) {
            Log.e(TAG, e, "CAUGHT: %", e.getMessage());
        }
        return false;
    }

    /**
     * This invokes the hidden method on WifiManager class by the same name and signature through
     * reflection.  It is very important to understand that his is invoking a private API method that
     * might change.
     * <p/>
     * This returns the state of the Access Point.  Enabled states are 3, 13 while disabled states
     * are 1, 11.
     * <p/>
     * {@link WifiUtils#WIFI_AP_STATE_ENABLED}
     * {@link WifiUtils#WIFI_AP_STATE_DISABLED}
     *
     * @return int state
     */
    public int getWifiApState()
    {
        try {
            Method method = wifiManager.getClass().getMethod("getWifiApState");
            if ( method != null ) {
                return (Integer) method.invoke(wifiManager);
            }
        }
        catch(Exception e) {
            Log.e(TAG, e, "CAUGHT: %", e.getMessage());
        }
        return WIFI_AP_STATE_FAILED;
    }

    /**
     * This invokes the hidden method on WifiManager class by the same name and signature through
     * reflection.  It is very important to understand that his is invoking a private API method that
     * might change.
     * <p/>
     * Currently it always only returns null.
     * @return WifiInfo
     */
    public WifiInfo getWifiApConfiguration()
    {
        try {
            Method method = wifiManager.getClass().getMethod("getWifiApConfiguration");
            if ( method != null ) {
                return (WifiInfo) method.invoke(wifiManager);
            }
        }
        catch(Exception e) {
            Log.e(TAG, e, "CAUGHT: %s", e.getMessage());
        }
        return null;
    }

    /**
     * This invokes the hidden method on WifiManager class by the same name and signature through
     * reflection.  It is very important to understand that his is invoking a private API method that
     * might change.
     * <p/>
     * Returns whether or not the Access Point is enabled.
     * @return boolean true or false
     */
    public boolean isWifiApEnabled()
    {
        try {
            Method method = wifiManager.getClass().getMethod("isWifiApEnabled");
            if ( method != null ) {
                return (Boolean) method.invoke(wifiManager);
            }
        }
        catch(Exception e) {
            Log.e(TAG, e, "CAUGHT: %", e.getMessage());
        }
        return false;
    }

    /**
     * Returns the SSID from the WifiManager
     * @return String SSID
     */
    public String getWifiSSID()
    {
        return wifiManager.getConnectionInfo().getSSID();
    }

    /**
     * This method will return an String[] array of two elements.  The first being the SSID if known,
     * and the second being the BSSID.  The reasons why the SSID might be an empty String would be
     * due to the fact that this device currently has its Access Point enabled and the SSID is not
     * available to the device, just the BSSID (i.e. MAC address).
     * 
     * @return String[] of {SSID, BSSID}, or null if device is not connected to a network
     */
    public String[] getSSIDS()
    {
        if ( isWifiApEnabled() ) {
            return new String[] {"", wifiManager.getConnectionInfo().getMacAddress().toLowerCase()};
        }
        else {
            String ssid = wifiManager.getConnectionInfo().getSSID();
            if ( ssid == null ) {
                return null;
            }

            for(String bssid : bssidMap.keySet()) {
                ScanMap scanMap = bssidMap.get(bssid);
                if ( scanMap != null ) {
                    ScanResult result = scanMap.getScan();
                    if ( result.SSID.equals( ssid ) ) {
                        return new String[] {result.SSID, result.BSSID};
                    }
                }
            }                         
        }
        return null;
    }
    
    /**
     * Converts an integer IP representation
     * @param i int
     * @return String formated address
     */
    public String intToIp(int i)
    {
        return ((i & 0xFF) + "."
                        + ((i >> 8) & 0xFF) + "."
                        + ((i >> 16) & 0xFF) + "."
                        + ((i >> 24) & 0xFF));
    }

    private Method[] findMethods(Class klass, String[] names)
    {
        Method[] results = new Method[names.length];

        int pos = 0;

        Method[] wmMethods = klass.getDeclaredMethods();
        for(Method method : wmMethods) {
            for(String name : names) {
                if(name.equals(method.getName())) {
                    results[pos++] = method;
                }
            }
        }

        return results;
    }

    /**
     * Create a Open Wifi configuration
     * <p/>
     * NOTE:  This requires the following permissions:
     * <code>
     * <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
     * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
     * <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
     * </code>
     *
     * @param context  The Context
     * @param ssidName The name of the SSID.
     */
    public void saveOpenConfig(Context context, String ssidName)
    {
        WifiConfiguration wc = new WifiConfiguration();
        wc.SSID = "\"" + ssidName + "\"";  //This should be in Quotes!!
        wc.status = WifiConfiguration.Status.DISABLED;
        wc.priority = 40;
        wc.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);

        WifiManager wifiManag = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        boolean res1 = wifiManag.setWifiEnabled(true);
        Log.d(TAG, "set Wifi enabled returned: " + res1);

        int res = wifiManager.addNetwork(wc);
        Log.d(TAG, "add Network returned: " + res);

        boolean es = wifiManager.saveConfiguration();
        Log.d(TAG, "saveConfiguration returned: " + es);

        boolean b = wifiManager.enableNetwork(res, true);
        Log.d(TAG, "enableNetwork returned: " + b);
    }

    /**
     * Create a WEP Wifi configuration
     * <p/>
     * NOTE:  This requires the following permissions:
     * <code>
     * <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
     * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
     * <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
     * </code>
     *
     * @param context  The Context
     * @param ssidName The name of the SSID.
     * @param wepKey   The WEP key for the new configuration
     */
    public void saveWEPConfig(Context context, String ssidName, String wepKey)
    {
        WifiConfiguration wc = new WifiConfiguration();
        wc.SSID = "\"" + ssidName + "\"";  //This should be in Quotes!!
        wc.hiddenSSID = true;
        wc.status = WifiConfiguration.Status.DISABLED;
        wc.priority = 40;
        wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        wc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        wc.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        wc.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
        wc.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
        wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
        wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);

        wc.wepKeys[0] = "\"" + wepKey + "\"";
        wc.wepTxKeyIndex = 0;

        WifiManager wifiManag = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        boolean res1 = wifiManag.setWifiEnabled(true);
        Log.d(TAG, "set Wifi enabled returned: " + res1);

        int res = wifiManager.addNetwork(wc);
        Log.d(TAG, "add Network returned: " + res);

        boolean es = wifiManager.saveConfiguration();
        Log.d(TAG, "saveConfiguration returned: " + es);

        boolean b = wifiManager.enableNetwork(res, true);
        Log.d(TAG, "enableNetwork returned: " + b);
    }

    /**
     * Get the WifiConfiguration by SSID.
     *
     * @param ssid    the SSID
     *
     * @return <code>WifiConfiguration</code>
     */
    public WifiConfiguration getWifiConfigurationBySSID(String ssid)
    {
        String fullName = "\"" + ssid + "\"";

        List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
        for(WifiConfiguration config : configs) {
            if(config.SSID.equals(fullName)) {
                return config;
            }
        }
        return null;
    }

    public void logWepConfig()
    {
        List<WifiConfiguration> item = wifiManager.getConfiguredNetworks();
        int i = item.size();

        Log.d(TAG, "----------------- Wep Configs -----------------");
        Log.d(TAG, "NO OF CONFIGS: " + i);

        i = 0;
        for(WifiConfiguration config : item) {
            i++;
            Log.d(TAG, "[" + i + "] SSID: " + config.SSID);
            Log.d(TAG, "PASSWORD: " + config.preSharedKey);
            Log.d(TAG, "ALLOWED ALGORITHMS: ");
            Log.d(TAG, "\tLEAP: " + config.allowedAuthAlgorithms.get(WifiConfiguration.AuthAlgorithm.LEAP));
            Log.d(TAG, "\tOPEN: " + config.allowedAuthAlgorithms.get(WifiConfiguration.AuthAlgorithm.OPEN));
            Log.d(TAG, "\tSHARED: " + config.allowedAuthAlgorithms.get(WifiConfiguration.AuthAlgorithm.SHARED));
            Log.d(TAG, "GROUP CIPHERS");
            Log.d(TAG, "\tCCMP: " + config.allowedGroupCiphers.get(WifiConfiguration.GroupCipher.CCMP));
            Log.d(TAG, "\tTKIP: " + config.allowedGroupCiphers.get(WifiConfiguration.GroupCipher.TKIP));
            Log.d(TAG, "\tWEP104: " + config.allowedGroupCiphers.get(WifiConfiguration.GroupCipher.WEP104));
            Log.d(TAG, "\tWEP40: " + config.allowedGroupCiphers.get(WifiConfiguration.GroupCipher.WEP40));
            Log.d(TAG, "KEYMGMT");
            Log.d(TAG, "\tIEEE8021X: " + config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.IEEE8021X));
            Log.d(TAG, "\tNONE: " + config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE));
            Log.d(TAG, "\tWPA_EAP: " + config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_EAP));
            Log.d(TAG, "\tWPA_PSK: " + config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK));
            Log.d(TAG, "PairWiseCipher");
            Log.d(TAG, "\tCCMP: " + config.allowedPairwiseCiphers.get(WifiConfiguration.PairwiseCipher.CCMP));
            Log.d(TAG, "\tNONE: " + config.allowedPairwiseCiphers.get(WifiConfiguration.PairwiseCipher.NONE));
            Log.d(TAG, "\tTKIP: " + config.allowedPairwiseCiphers.get(WifiConfiguration.PairwiseCipher.TKIP));
            Log.d(TAG, "Protocols");
            Log.d(TAG, "\tRSN: " + config.allowedProtocols.get(WifiConfiguration.Protocol.RSN));
            Log.d(TAG, "\tWPA: " + config.allowedProtocols.get(WifiConfiguration.Protocol.WPA));
            Log.d(TAG, "WEP Key Strings");
            String[] wepKeys = config.wepKeys;
            Log.d(TAG, "\tWEP KEY 0: " + wepKeys[0]);
            Log.d(TAG, "\tWEP KEY 1: " + wepKeys[1]);
            Log.d(TAG, "\tWEP KEY 2: " + wepKeys[2]);
            Log.d(TAG, "\tWEP KEY 3: " + wepKeys[3]);
        }
    }

    public void logEapConfig(Context context, BufferedWriter out)
    {
        /*Get All WIfi configurations*/
        List<WifiConfiguration> configList = wifiManager.getConfiguredNetworks();
        /*Now we need to search appropriate configuration i.e. with name SSID_Name*/
        for(int i = 0; i < configList.size(); i++) {
            if(configList.get(i).SSID.contentEquals("\"SSID_NAME\"")) {
                /*We found the appropriate config now read all config details*/
                Iterator<WifiConfiguration> iter = configList.iterator();
                WifiConfiguration config = configList.get(i);

                /*I dont think these fields have anything to do with EAP config but still will
                 * print these to be on safe side*/
                Log.d(TAG, "[SSID]" + config.SSID);
                Log.d(TAG, "[BSSID]" + config.BSSID);
                Log.d(TAG, "[HIDDEN SSID]" + config.hiddenSSID);
                Log.d(TAG, "[PASSWORD]" + config.preSharedKey);
                Log.d(TAG, "[ALLOWED ALGORITHMS]");
                Log.d(TAG, "[LEAP]" + config.allowedAuthAlgorithms.get(WifiConfiguration.AuthAlgorithm.LEAP));
                Log.d(TAG, "[OPEN]" + config.allowedAuthAlgorithms.get(WifiConfiguration.AuthAlgorithm.OPEN));
                Log.d(TAG, "[SHARED]" + config.allowedAuthAlgorithms.get(WifiConfiguration.AuthAlgorithm.SHARED));
                Log.d(TAG, "[GROUP CIPHERS]");
                Log.d(TAG, "[CCMP]" + config.allowedGroupCiphers.get(WifiConfiguration.GroupCipher.CCMP));
                Log.d(TAG, "[TKIP]" + config.allowedGroupCiphers.get(WifiConfiguration.GroupCipher.TKIP));
                Log.d(TAG, "[WEP104]" + config.allowedGroupCiphers.get(WifiConfiguration.GroupCipher.WEP104));
                Log.d(TAG, "[WEP40]" + config.allowedGroupCiphers.get(WifiConfiguration.GroupCipher.WEP40));
                Log.d(TAG, "[KEYMGMT]");
                Log.d(TAG, "[IEEE8021X]" + config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.IEEE8021X));
                Log.d(TAG, "[NONE]" + config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE));
                Log.d(TAG, "[WPA_EAP]" + config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_EAP));
                Log.d(TAG, "[WPA_PSK]" + config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK));
                Log.d(TAG, "[PairWiseCipher]");
                Log.d(TAG, "[CCMP]" + config.allowedPairwiseCiphers.get(WifiConfiguration.PairwiseCipher.CCMP));
                Log.d(TAG, "[NONE]" + config.allowedPairwiseCiphers.get(WifiConfiguration.PairwiseCipher.NONE));
                Log.d(TAG, "[TKIP]" + config.allowedPairwiseCiphers.get(WifiConfiguration.PairwiseCipher.TKIP));
                Log.d(TAG, "[Protocols]");
                Log.d(TAG, "[RSN]" + config.allowedProtocols.get(WifiConfiguration.Protocol.RSN));
                Log.d(TAG, "[WPA]" + config.allowedProtocols.get(WifiConfiguration.Protocol.WPA));
                Log.d(TAG, "[PRE_SHARED_KEY]" + config.preSharedKey);
                Log.d(TAG, "[WEP Key Strings]");
                String[] wepKeys = config.wepKeys;
                Log.d(TAG, "[WEP KEY 0]" + wepKeys[0]);
                Log.d(TAG, "[WEP KEY 1]" + wepKeys[1]);
                Log.d(TAG, "[WEP KEY 2]" + wepKeys[2]);
                Log.d(TAG, "[WEP KEY 3]" + wepKeys[3]);

                /*reflection magic*/
                /*These are the fields we are really interested in*/
                try {
                    // Let the magic start
                    Class[] wcClasses = WifiConfiguration.class.getClasses();
                    // null for overzealous java compiler
                    Class wcEnterpriseField = null;

                    for(Class wcClass : wcClasses) {
                        if(wcClass.getName().equals(INT_ENTERPRISEFIELD_NAME)) {
                            wcEnterpriseField = wcClass;
                            break;
                        }
                    }
                    boolean noEnterpriseFieldType = false;
                    if(wcEnterpriseField == null) {
                        noEnterpriseFieldType = true; // Cupcake/Donut access enterprise settings directly
                    }

                    Field wcefAnonymousId = null, wcefCaCert = null, wcefClientCert = null, wcefEap = null, wcefIdentity = null, wcefPassword = null, wcefPhase2 = null, wcefPrivateKey = null;
                    Field[] wcefFields = WifiConfiguration.class.getFields();
                    // Dispatching Field vars
                    for(Field wcefField : wcefFields) {
                        if(wcefField.getName().trim().equals(INT_ANONYMOUS_IDENTITY)) {
                            wcefAnonymousId = wcefField;
                        }
                        else if(wcefField.getName().trim().equals(INT_CA_CERT)) {
                            wcefCaCert = wcefField;
                        }
                        else if(wcefField.getName().trim().equals(INT_CLIENT_CERT)) {
                            wcefClientCert = wcefField;
                        }
                        else if(wcefField.getName().trim().equals(INT_EAP)) {
                            wcefEap = wcefField;
                        }
                        else if(wcefField.getName().trim().equals(INT_IDENTITY)) {
                            wcefIdentity = wcefField;
                        }
                        else if(wcefField.getName().trim().equals(INT_PASSWORD)) {
                            wcefPassword = wcefField;
                        }
                        else if(wcefField.getName().trim().equals(INT_PHASE2)) {
                            wcefPhase2 = wcefField;
                        }
                        else if(wcefField.getName().trim().equals(INT_PRIVATE_KEY)) {
                            wcefPrivateKey = wcefField;
                        }
                    }
                    Method wcefSetValue = null;
                    if(!noEnterpriseFieldType) {
                        for(Method m : wcEnterpriseField.getMethods())
                        //System.out.println(m.getName());
                        {
                            if(m.getName().trim().equals("value")) {
                                wcefSetValue = m;
                                break;
                            }
                        }
                    }

                    /*EAP Method*/
                    String result = null;
                    Object obj = null;
                    if(!noEnterpriseFieldType) {
                        obj = wcefSetValue.invoke(wcefEap.get(config), null);
                        String retval = (String) obj;
                        Log.d(TAG, "[EAP METHOD]" + retval);
                    }

                    /*phase 2*/
                    if(!noEnterpriseFieldType) {
                        result = (String) wcefSetValue.invoke(wcefPhase2.get(config), null);
                        Log.d(TAG, "[EAP PHASE 2 AUTHENTICATION]" + result);
                    }

                    /*Anonymous Identity*/
                    if(!noEnterpriseFieldType) {
                        result = (String) wcefSetValue.invoke(wcefAnonymousId.get(config), null);
                        Log.d(TAG, "[EAP ANONYMOUS IDENTITY]" + result);
                    }

                    /*CA certificate*/
                    if(!noEnterpriseFieldType) {
                        result = (String) wcefSetValue.invoke(wcefCaCert.get(config), null);
                        Log.d(TAG, "[EAP CA CERTIFICATE]" + result);
                    }

                    /*private key*/
                    if(!noEnterpriseFieldType) {
                        result = (String) wcefSetValue.invoke(wcefPrivateKey.get(config), null);
                        Log.d(TAG, "[EAP PRIVATE KEY]" + result);
                    }

                    /*Identity*/
                    if(!noEnterpriseFieldType) {
                        result = (String) wcefSetValue.invoke(wcefIdentity.get(config), null);
                        Log.d(TAG, "[EAP IDENTITY]" + result);
                    }

                    /*Password*/
                    if(!noEnterpriseFieldType) {
                        result = (String) wcefSetValue.invoke(wcefPassword.get(config), null);
                        Log.d(TAG, "[EAP PASSWORD]" + result);
                    }

                    /*client certificate*/
                    if(!noEnterpriseFieldType) {
                        result = (String) wcefSetValue.invoke(wcefClientCert.get(config), null);
                        Log.d(TAG, "[EAP CLIENT CERT]" + result);
                    }
                }
                catch(Exception e) {
                    e.printStackTrace();
                }

            }
        }
    }

    public void saveEapConfig(Context context, String ssidName, String passString, String userName)
    {
        /********************************Configuration Strings****************************************************/
        final String ENTERPRISE_EAP = "TLS";
        final String ENTERPRISE_CLIENT_CERT = "keystore://USRCERT_CertificateName";
        final String ENTERPRISE_PRIV_KEY = "keystore://USRPKEY_CertificateName";

        /*Optional Params- My wireless Doesn't use these*/
        final String ENTERPRISE_PHASE2 = "";
        final String ENTERPRISE_ANON_IDENT = "ABC";
        final String ENTERPRISE_CA_CERT = "";
        /********************************Configuration Strings****************************************************/

        /*Create a WifiConfig*/
        WifiConfiguration selectedConfig = new WifiConfiguration();

        /*AP Name*/
        selectedConfig.SSID = "\"" + ssidName + "\"";

        /*Priority*/
        selectedConfig.priority = 40;

        /*Enable Hidden SSID*/
        selectedConfig.hiddenSSID = true;

        /*Key Mgmnt*/
        selectedConfig.allowedKeyManagement.clear();
        selectedConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.IEEE8021X);
        selectedConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP);

        /*Group Ciphers*/
        selectedConfig.allowedGroupCiphers.clear();
        selectedConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        selectedConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        selectedConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
        selectedConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);

        /*Pairwise ciphers*/
        selectedConfig.allowedPairwiseCiphers.clear();
        selectedConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        selectedConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);

        /*Protocols*/
        selectedConfig.allowedProtocols.clear();
        selectedConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        selectedConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);

        // Enterprise Settings
        // Reflection magic here too, need access to non-public APIs
        try {
            // Let the magic start
            Class[] wcClasses = WifiConfiguration.class.getClasses();
            // null for overzealous java compiler
            Class wcEnterpriseField = null;

            for(Class wcClass : wcClasses) {
                if(wcClass.getName().equals(INT_ENTERPRISEFIELD_NAME)) {
                    wcEnterpriseField = wcClass;
                    break;
                }
            }
            boolean noEnterpriseFieldType = false;
            if(wcEnterpriseField == null) {
                noEnterpriseFieldType = true; // Cupcake/Donut access enterprise settings directly
            }

            Field wcefAnonymousId = null, wcefCaCert = null, wcefClientCert = null, wcefEap = null, wcefIdentity = null, wcefPassword = null, wcefPhase2 = null, wcefPrivateKey = null;
            Field[] wcefFields = WifiConfiguration.class.getFields();
            // Dispatching Field vars
            for(Field wcefField : wcefFields) {
                if(wcefField.getName().equals(INT_ANONYMOUS_IDENTITY)) {
                    wcefAnonymousId = wcefField;
                }
                else if(wcefField.getName().equals(INT_CA_CERT)) {
                    wcefCaCert = wcefField;
                }
                else if(wcefField.getName().equals(INT_CLIENT_CERT)) {
                    wcefClientCert = wcefField;
                }
                else if(wcefField.getName().equals(INT_EAP)) {
                    wcefEap = wcefField;
                }
                else if(wcefField.getName().equals(INT_IDENTITY)) {
                    wcefIdentity = wcefField;
                }
                else if(wcefField.getName().equals(INT_PASSWORD)) {
                    wcefPassword = wcefField;
                }
                else if(wcefField.getName().equals(INT_PHASE2)) {
                    wcefPhase2 = wcefField;
                }
                else if(wcefField.getName().equals(INT_PRIVATE_KEY)) {
                    wcefPrivateKey = wcefField;
                }
            }


            Method wcefSetValue = null;
            if(!noEnterpriseFieldType) {
                for(Method m : wcEnterpriseField.getMethods())
                //System.out.println(m.getName());
                {
                    if(m.getName().trim().equals("setValue")) {
                        wcefSetValue = m;
                    }
                }
            }

            /*EAP Method*/
            if(!noEnterpriseFieldType) {
                wcefSetValue.invoke(wcefEap.get(selectedConfig), ENTERPRISE_EAP);
            }

            /*EAP Phase 2 Authentication*/
            if(!noEnterpriseFieldType) {
                wcefSetValue.invoke(wcefPhase2.get(selectedConfig), ENTERPRISE_PHASE2);
            }

            /*EAP Anonymous Identity*/
            if(!noEnterpriseFieldType) {
                wcefSetValue.invoke(wcefAnonymousId.get(selectedConfig), ENTERPRISE_ANON_IDENT);
            }

            /*EAP CA Certificate*/
            if(!noEnterpriseFieldType) {
                wcefSetValue.invoke(wcefCaCert.get(selectedConfig), ENTERPRISE_CA_CERT);
            }

            /*EAP Private key*/
            if(!noEnterpriseFieldType) {
                wcefSetValue.invoke(wcefPrivateKey.get(selectedConfig), ENTERPRISE_PRIV_KEY);
            }

            /*EAP Identity*/
            if(!noEnterpriseFieldType) {
                wcefSetValue.invoke(wcefIdentity.get(selectedConfig), userName);
            }

            /*EAP Password*/
            if(!noEnterpriseFieldType) {
                wcefSetValue.invoke(wcefPassword.get(selectedConfig), passString);
            }

            /*EAp Client certificate*/
            if(!noEnterpriseFieldType) {
                wcefSetValue.invoke(wcefClientCert.get(selectedConfig), ENTERPRISE_CLIENT_CERT);
            }

            // Adhoc for CM6
            // if non-CM6 fails gracefully thanks to nested try-catch

            try {
                Field wcAdhoc = WifiConfiguration.class.getField("adhocSSID");
                Field wcAdhocFreq = WifiConfiguration.class.getField("frequency");
                //wcAdhoc.setBoolean(selectedConfig, prefs.getBoolean(PREF_ADHOC,
                //      false));
                wcAdhoc.setBoolean(selectedConfig, false);
                int freq = 2462;    // default to channel 11
                //int freq = Integer.parseInt(prefs.getString(PREF_ADHOC_FREQUENCY,
                //"2462"));     // default to channel 11
                //System.err.println(freq);
                wcAdhocFreq.setInt(selectedConfig, freq);
            }
            catch(Exception e) {
                e.printStackTrace();
            }

        }
        catch(Exception e) {
            // TODO Auto-generated catch block
            // FIXME As above, what should I do here?
            e.printStackTrace();
        }

        boolean res1 = wifiManager.setWifiEnabled(true);
        int res = wifiManager.addNetwork(selectedConfig);
        Log.d(TAG, "add Network returned " + res);
        boolean b = wifiManager.enableNetwork(selectedConfig.networkId, false);
        Log.d(TAG, "enableNetwork returned " + b);
        boolean c = wifiManager.saveConfiguration();
        Log.d(TAG, "Save configuration returned " + c);
        boolean d = wifiManager.enableNetwork(res, true);
        Log.d(TAG, "enableNetwork returned " + d);
    }

    public boolean hasPassword(String bssid)
    {
        List<WifiConfiguration> item = wifiManager.getConfiguredNetworks();
        for(WifiConfiguration config : item) {

            if(bssid.equalsIgnoreCase(config.BSSID)) {
                return "*".equals(config.preSharedKey);
            }
        }

        return false;
    }
}
