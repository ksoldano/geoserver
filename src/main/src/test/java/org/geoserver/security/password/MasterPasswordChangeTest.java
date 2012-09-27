package org.geoserver.security.password;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.security.GeoServerSecurityTestSupport;
import org.geoserver.security.auth.GeoServerRootAuthenticationProvider;
import org.geoserver.security.validation.MasterPasswordChangeException;
import org.geoserver.test.SystemTest;
import org.geoserver.test.TestSetup;
import org.geoserver.test.TestSetupFrequency;
import org.geotools.data.DataUtilities;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@TestSetup(run=TestSetupFrequency.REPEAT)
@Category(SystemTest.class)
public class MasterPasswordChangeTest extends GeoServerSecurityTestSupport {

    @Override
    protected void setUpSpring(List<String> springContextLocations) {
        super.setUpSpring(springContextLocations);
        springContextLocations.add(
            getClass().getResource(getClass().getSimpleName() + "-context.xml").toString());
    }

    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);
        applicationContext.getBeanFactory()
            .registerSingleton("testMasterPasswordProvider", new TestMasterPasswordProvider());
    }

    @Test
    public void testMasterPasswordChange() throws Exception {
        // keytool -storepasswd -new geoserver1 -storepass geoserver -storetype jceks -keystore geoserver.jks
        
        
        String masterPWAsString = getMasterPassword();
        MasterPasswordConfig config = getSecurityManager().getMasterPasswordConfig();
        
        URLMasterPasswordProviderConfig mpConfig = (URLMasterPasswordProviderConfig) 
            getSecurityManager().loadMasterPassswordProviderConfig(config.getProviderName());
        
        assertTrue(mpConfig.getURL().toString().endsWith(URLMasterPasswordProviderConfig.MASTER_PASSWD_FILENAME));
        getSecurityManager().getKeyStoreProvider().reloadKeyStore();
        
        try {
            getSecurityManager().saveMasterPasswordConfig(config, null, null, null);
            fail();
        } catch (MasterPasswordChangeException ex) {
        }
        
        ///// First change using rw_url
        mpConfig = new URLMasterPasswordProviderConfig();
        mpConfig.setName("rw");
        mpConfig.setClassName(URLMasterPasswordProvider.class.getCanonicalName());
        mpConfig.setReadOnly(false);

        File tmp = new File(getSecurityManager().getSecurityRoot(),"mpw1.properties");
        mpConfig.setURL(DataUtilities.fileToURL(tmp));
        getSecurityManager().saveMasterPasswordProviderConfig(mpConfig);
        
        config = getSecurityManager().getMasterPasswordConfig();
        config.setProviderName(mpConfig.getName());
        getSecurityManager().saveMasterPasswordConfig(
            config, masterPWAsString.toCharArray(), "geoserver1".toCharArray(), "geoserver1".toCharArray());
        assertEquals("geoserver1", getMasterPassword());
        
        getSecurityManager().getKeyStoreProvider().getConfigPasswordKey();
        
        /////////////// change with ro url
        mpConfig = new URLMasterPasswordProviderConfig();
        mpConfig.setName("ro");
        mpConfig.setClassName(URLMasterPasswordProvider.class.getCanonicalName());
        mpConfig.setReadOnly(true);
        
        tmp = new File(getSecurityManager().getSecurityRoot(),"mpw2.properties");
        mpConfig.setURL(DataUtilities.fileToURL(tmp));
        
        FileUtils.writeStringToFile(tmp, "geoserver2");
        
        getSecurityManager().saveMasterPasswordProviderConfig(mpConfig);
        config = getSecurityManager().getMasterPasswordConfig();
        config.setProviderName("ro");
        
        getSecurityManager().saveMasterPasswordConfig(
            config, "geoserver1".toCharArray(), null, "geoserver2".toCharArray());
        
        assertEquals("geoserver2",getMasterPassword());
        getSecurityManager().getKeyStoreProvider().getConfigPasswordKey();
        
        /////////////////////// change simulating spring injection
        MasterPasswordProviderConfig mpConfig2 = new MasterPasswordProviderConfig();
        mpConfig2.setName("test");
        mpConfig2.setClassName(TestMasterPasswordProvider.class.getCanonicalName());
        getSecurityManager().saveMasterPasswordProviderConfig(mpConfig2);
        
        config =getSecurityManager().getMasterPasswordConfig();
        config.setProviderName("test");
        getSecurityManager().saveMasterPasswordConfig(
            config, "geoserver2".toCharArray(), "geoserver3".toCharArray(), "geoserver3".toCharArray());
        
        // now, a geoserver restart should appear, simulate with
        getSecurityManager().getKeyStoreProvider().commitMasterPasswordChange();

        //////////
        assertEquals("geoserver3",getMasterPassword());
        getSecurityManager().getKeyStoreProvider().getConfigPasswordKey();
    }

    @Test
    public void testRootLoginAfterMasterPasswdChange() throws Exception {
        String masterPWAsString = getMasterPassword();

        GeoServerRootAuthenticationProvider authProvider = new GeoServerRootAuthenticationProvider();
        authProvider.setSecurityManager(getSecurityManager());

        Authentication auth = new UsernamePasswordAuthenticationToken("root", masterPWAsString);
        auth = authProvider.authenticate(auth);
        assertTrue(auth.isAuthenticated());

        MasterPasswordConfig config = getSecurityManager().getMasterPasswordConfig();

        getSecurityManager().saveMasterPasswordConfig(config, masterPWAsString.toCharArray(), 
            "geoserver1".toCharArray(), "geoserver1".toCharArray());
        assertEquals("geoserver1", getMasterPassword());

        auth = new UsernamePasswordAuthenticationToken("root", masterPWAsString);
        assertNull(authProvider.authenticate(auth));
        assertFalse(auth.isAuthenticated());

        auth = new UsernamePasswordAuthenticationToken("root", "geoserver1");
        auth = authProvider.authenticate(auth);
        assertTrue(auth.isAuthenticated());
    }
}
