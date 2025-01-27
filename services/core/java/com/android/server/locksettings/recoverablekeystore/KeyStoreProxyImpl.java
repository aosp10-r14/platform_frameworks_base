/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.locksettings.recoverablekeystore;

import android.security.keystore2.AndroidKeyStoreProvider;

import java.io.IOException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

/**
 * Implementation of {@link KeyStoreProxy} that delegates all method calls to the {@link KeyStore}.
 */
public class KeyStoreProxyImpl implements KeyStoreProxy {

    private final KeyStore mKeyStore;

    /**
     * TODO This function redirects keystore access to the legacy keystore during a transitional
     *      phase during which not all calling code has been adjusted to use Keystore 2.0.
     *      This can be reverted to a constant of "AndroidKeyStore" when b/171305684 is complete.
     *      The specific bug for this component is b/171305545.
     */
    static String androidKeystoreProviderName() {
        if (AndroidKeyStoreProvider.isInstalled()) {
            return "AndroidKeyStoreLegacy";
        } else {
            return "AndroidKeyStore";
        }

    }

    /**
     * A new instance, delegating to {@code keyStore}.
     */
    public KeyStoreProxyImpl(KeyStore keyStore) {
        mKeyStore = keyStore;
    }

    @Override
    public boolean containsAlias(String alias) throws KeyStoreException {
        return mKeyStore.containsAlias(alias);
    }

    @Override
    public Key getKey(String alias, char[] password)
            throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        return mKeyStore.getKey(alias, password);
    }

    @Override
    public void setEntry(String alias, KeyStore.Entry entry, KeyStore.ProtectionParameter protParam)
            throws KeyStoreException {
        mKeyStore.setEntry(alias, entry, protParam);
    }

    @Override
    public void deleteEntry(String alias) throws KeyStoreException {
        mKeyStore.deleteEntry(alias);
    }

    /**
     * Returns AndroidKeyStore-provided {@link KeyStore}, having already invoked
     * {@link KeyStore#load(KeyStore.LoadStoreParameter)}.
     *
     * @throws KeyStoreException if there was a problem getting or initializing the key store.
     */
    public static KeyStore getAndLoadAndroidKeyStore() throws KeyStoreException {
        KeyStore keyStore = KeyStore.getInstance(androidKeystoreProviderName());
        try {
            keyStore.load(/*param=*/ null);
        } catch (CertificateException | IOException | NoSuchAlgorithmException e) {
            // Should never happen.
            throw new KeyStoreException("Unable to load keystore.", e);
        }
        return keyStore;
    }
}
