/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.security.KeyStore;
import android.util.EventLog;
import android.util.Log;
import android.util.MutableBoolean;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.ListView;

import com.android.internal.widget.LockPatternUtils;

import java.util.List;

public class ChooseLockGeneric extends SettingsActivity {
    public static final String CONFIRM_CREDENTIALS = "confirm_credentials";

    @Override
    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, ChooseLockGenericFragment.class.getName());
        return modIntent;
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        if (ChooseLockGenericFragment.class.getName().equals(fragmentName)) return true;
        return false;
    }

    public static class InternalActivity extends ChooseLockGeneric {
    }

    public static class ChooseLockGenericFragment extends SettingsPreferenceFragment {
        private static final int MIN_PASSWORD_LENGTH = 4;
        private static final String KEY_UNLOCK_BACKUP_INFO = "unlock_backup_info";
        private static final String KEY_UNLOCK_SET_OFF = "unlock_set_off";
        private static final String KEY_UNLOCK_SET_NONE = "unlock_set_none";
        private static final String KEY_UNLOCK_SET_BIOMETRIC_WEAK = "unlock_set_biometric_weak";
        private static final String KEY_UNLOCK_SET_PIN = "unlock_set_pin";
        private static final String KEY_UNLOCK_SET_PASSWORD = "unlock_set_password";
        private static final String KEY_UNLOCK_SET_PATTERN = "unlock_set_pattern";
        private static final int CONFIRM_EXISTING_REQUEST = 100;
        private static final int FALLBACK_REQUEST = 101;
        private static final int ENABLE_ENCRYPTION_REQUEST = 102;
        private static final String PASSWORD_CONFIRMED = "password_confirmed";

        private static final String WAITING_FOR_CONFIRMATION = "waiting_for_confirmation";
        private static final String FINISH_PENDING = "finish_pending";
        public static final String MINIMUM_QUALITY_KEY = "minimum_quality";
        public static final String ENCRYPT_REQUESTED_QUALITY = "encrypt_requested_quality";
        public static final String ENCRYPT_REQUESTED_DISABLED = "encrypt_requested_disabled";

        // INTEL_LPAL
        private static final String INTEL_LPAL_TAG = "INTEL_LPAL_ChooseLockGeneric";
        public static final String VTSV_FLAG = "vtsv";
        public static final String VTSV_OPERATION = "operation";
        public static final int VTSV_BACK_UP_OPERATION = 1;
        public static final int VTSV_FALLBACK_OPERATION  = 2;
        public static final int VTSV_DISABLE_VU_OPERATION  = 3;
        private static final String KEY_UNLOCK_SET_VOICE = "unlock_set_voice";
        // INTEL_LPAL end

        private static final boolean ALWAY_SHOW_TUTORIAL = true;

        private ChooseLockSettingsHelper mChooseLockSettingsHelper;
        private DevicePolicyManager mDPM;
        private KeyStore mKeyStore;
        private boolean mPasswordConfirmed = false;
        private boolean mWaitingForConfirmation = false;
        private boolean mFinishPending = false;
        private int mEncryptionRequestQuality;
        private boolean mEncryptionRequestDisabled;
        private boolean mRequirePassword;
        private LockPatternUtils mLockPatternUtils;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            mDPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            mKeyStore = KeyStore.getInstance();
            mChooseLockSettingsHelper = new ChooseLockSettingsHelper(this.getActivity());
            mLockPatternUtils = new LockPatternUtils(getActivity());

            // Defaults to needing to confirm credentials
            final boolean confirmCredentials = getActivity().getIntent()
                .getBooleanExtra(CONFIRM_CREDENTIALS, true);
            if (getActivity() instanceof ChooseLockGeneric.InternalActivity) {
                mPasswordConfirmed = !confirmCredentials;
            }

            if (savedInstanceState != null) {
                mPasswordConfirmed = savedInstanceState.getBoolean(PASSWORD_CONFIRMED);
                mWaitingForConfirmation = savedInstanceState.getBoolean(WAITING_FOR_CONFIRMATION);
                mFinishPending = savedInstanceState.getBoolean(FINISH_PENDING);
                mEncryptionRequestQuality = savedInstanceState.getInt(ENCRYPT_REQUESTED_QUALITY);
                mEncryptionRequestDisabled = savedInstanceState.getBoolean(
                        ENCRYPT_REQUESTED_DISABLED);
            }

            // INTEL_LPAL
            final boolean isVtsv = getActivity().getIntent().getBooleanExtra(VTSV_FLAG, false);
            final int operation = getActivity().getIntent()
                    .getIntExtra(VTSV_OPERATION, VTSV_FALLBACK_OPERATION);

            if (mPasswordConfirmed) {
                if (mKeyStore != null) { // add this judgement for fixing KW issuse
                    if (isVtsv) {
                        // INTEL_LPAL: if intent is sent by vtsv config app, "isVtsv" will be true
                        Log.d(INTEL_LPAL_TAG, "activity is launched by intel vtsv configure UI!");
                        handleVtsvOperation(operation);
                    } else {
                        updatePreferencesOrFinish();
                    }
                }
            } else if (!mWaitingForConfirmation) {
                ChooseLockSettingsHelper helper =
                        new ChooseLockSettingsHelper(this.getActivity(), this);
                if (!helper.launchConfirmationActivity(CONFIRM_EXISTING_REQUEST, null, null)) {
                    mPasswordConfirmed = true; // no password set, so no need to confirm
                    if (isVtsv) {
                        // INTEL_LPAL: if intent is sent by vtsv config app, "isVtsv" will be true
                        Log.d(INTEL_LPAL_TAG, "activity is launched by intel vtsv configure UI!");
                        handleVtsvOperation(operation);
                    } else {
                        updatePreferencesOrFinish();
                    }
                } else {
                    mWaitingForConfirmation = true;
                }
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            if (mFinishPending) {
                mFinishPending = false;
                finish();
            }
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
                Preference preference) {
            final String key = preference.getKey();
            boolean handled = true;

            EventLog.writeEvent(EventLogTags.LOCK_SCREEN_TYPE, key);

            if (KEY_UNLOCK_SET_OFF.equals(key)) {
                updateUnlockMethodAndFinish(
                        DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED, true);
            } else if (KEY_UNLOCK_SET_NONE.equals(key)) {
                updateUnlockMethodAndFinish(
                        DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED, false);
            } else if (KEY_UNLOCK_SET_BIOMETRIC_WEAK.equals(key)) {
                maybeEnableEncryption(
                        DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_WEAK, false);
            }else if (KEY_UNLOCK_SET_PATTERN.equals(key)) {
                maybeEnableEncryption(
                        DevicePolicyManager.PASSWORD_QUALITY_SOMETHING, false);
            } else if (KEY_UNLOCK_SET_PIN.equals(key)) {
                maybeEnableEncryption(
                        DevicePolicyManager.PASSWORD_QUALITY_NUMERIC, false);
            } else if (KEY_UNLOCK_SET_PASSWORD.equals(key)) {
                maybeEnableEncryption(
                        DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC, false);
            }
            // INTEL_LPAL start
            else if (KEY_UNLOCK_SET_VOICE.equals(key)) {
                Log.d(INTEL_LPAL_TAG, "voice unlock preference is clicked!");
                updateUnlockMethodAndFinish(
                        DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_VOICE_WEAK, false);
            }
            // INTEL_LPAL end
            else {
                handled = false;
            }
            return handled;
        }

        /**
         * If the device has encryption already enabled, then ask the user if they
         * also want to encrypt the phone with this password.
         *
         * @param quality
         * @param disabled
         */
        private void maybeEnableEncryption(int quality, boolean disabled) {
            if (Process.myUserHandle().isOwner() && LockPatternUtils.isDeviceEncryptionEnabled()) {
                mEncryptionRequestQuality = quality;
                mEncryptionRequestDisabled = disabled;
                // If accessibility is enabled and the user hasn't seen this dialog before, set the
                // default state to agree with that which is compatible with accessibility
                // (password not required).
                final boolean accEn = AccessibilityManager.getInstance(getActivity()).isEnabled();
                final boolean required = mLockPatternUtils.isCredentialRequiredToDecrypt(!accEn);
                Intent intent = EncryptionInterstitial.createStartIntent(
                        getActivity(), quality, required);
                startActivityForResult(intent, ENABLE_ENCRYPTION_REQUEST);
            } else {
                mRequirePassword = false; // device encryption not enabled or not device owner.
                updateUnlockMethodAndFinish(quality, disabled);
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View v = super.onCreateView(inflater, container, savedInstanceState);
            final boolean onlyShowFallback = getActivity().getIntent()
                    .getBooleanExtra(LockPatternUtils.LOCKSCREEN_BIOMETRIC_WEAK_FALLBACK, false);
            if (onlyShowFallback) {
                View header = v.inflate(getActivity(),
                        R.layout.weak_biometric_fallback_header, null);
                ((ListView) v.findViewById(android.R.id.list)).addHeaderView(header, null, false);
            }

            // INTEL_LPAL start
            final boolean onlyShowVoiceFallback = getActivity().getIntent().getBooleanExtra(
                    LockPatternUtils.LOCKSCREEN_BIOMETRIC_VOICE_WEAK_FALLBACK, false);
            if (onlyShowVoiceFallback) {
                View header = v.inflate(getActivity(),
                        R.layout.weak_biometric_fallback_header_voice, null);
                ((ListView) v.findViewById(android.R.id.list)).addHeaderView(header, null, false);
            }
            // INTEL_LPAL end

            return v;
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            mWaitingForConfirmation = false;
            if (requestCode == CONFIRM_EXISTING_REQUEST && resultCode == Activity.RESULT_OK) {
                mPasswordConfirmed = true;

                // INTEL_LPAL start
                final boolean isVtsv = getActivity().getIntent().getBooleanExtra(VTSV_FLAG, false);
                if (isVtsv) {
                    final int operation = getActivity().getIntent().getIntExtra(
                            VTSV_OPERATION, VTSV_FALLBACK_OPERATION);
                    handleVtsvOperation(operation);
                    return;
                }
                // INTEL_LPAL end

                updatePreferencesOrFinish();
            } else if (requestCode == FALLBACK_REQUEST) {
                mChooseLockSettingsHelper.utils().deleteTempGallery();
                getActivity().setResult(resultCode);
                finish();
            } else if (requestCode == ENABLE_ENCRYPTION_REQUEST
                    && resultCode == Activity.RESULT_OK) {
                mRequirePassword = data.getBooleanExtra(
                        EncryptionInterstitial.EXTRA_REQUIRE_PASSWORD, true);
                updateUnlockMethodAndFinish(mEncryptionRequestQuality, mEncryptionRequestDisabled);
            } else {
                getActivity().setResult(Activity.RESULT_CANCELED);
                finish();
            }
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            // Saved so we don't force user to re-enter their password if configuration changes
            outState.putBoolean(PASSWORD_CONFIRMED, mPasswordConfirmed);
            outState.putBoolean(WAITING_FOR_CONFIRMATION, mWaitingForConfirmation);
            outState.putBoolean(FINISH_PENDING, mFinishPending);
            outState.putInt(ENCRYPT_REQUESTED_QUALITY, mEncryptionRequestQuality);
            outState.putBoolean(ENCRYPT_REQUESTED_DISABLED, mEncryptionRequestDisabled);
        }

        private void updatePreferencesOrFinish() {
            Intent intent = getActivity().getIntent();
            int quality = intent.getIntExtra(LockPatternUtils.PASSWORD_TYPE_KEY, -1);
            if (quality == -1) {
                // If caller didn't specify password quality, show UI and allow the user to choose.
                quality = intent.getIntExtra(MINIMUM_QUALITY_KEY, -1);
                MutableBoolean allowBiometric = new MutableBoolean(false);
                quality = upgradeQuality(quality, allowBiometric);
                final PreferenceScreen prefScreen = getPreferenceScreen();
                if (prefScreen != null) {
                    prefScreen.removeAll();
                }

                // INTEL_LPAL
                if (mChooseLockSettingsHelper.utils().isBiometricVoiceWeakInstalled()) {
                    Log.d(INTEL_LPAL_TAG, "add Preference: security_settings_picker_lpal");
                    addPreferencesFromResource(R.xml.security_settings_picker_lpal);
                } else {
                    addPreferencesFromResource(R.xml.security_settings_picker);
                }

                disableUnusablePreferences(quality, allowBiometric);
                updatePreferenceSummaryIfNeeded();
            } else {
                updateUnlockMethodAndFinish(quality, false);
            }
        }

        /**
         * INTEL_LPAL: when the request comes from INTEL_LPAL apk, handle it's operation request.
         */
        private void handleVtsvOperation(int operation) {

            if (operation == VTSV_BACK_UP_OPERATION) {
                Log.d(INTEL_LPAL_TAG, "vtsv operation: backup");
                updatePreferencesOrFinish();
            } else if (operation == VTSV_DISABLE_VU_OPERATION) {
                Log.d(INTEL_LPAL_TAG, "vtsv operation: disable voice unlock");
                updatePreferencesOrFinish();

            } else {
                Log.d(INTEL_LPAL_TAG, "vtsv operation: fallback");
                updateUnlockMethodAndFinish(
                        DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_VOICE_WEAK, false);
            }

        }

        /** increases the quality if necessary, and returns whether biometric is allowed */
        private int upgradeQuality(int quality, MutableBoolean allowBiometric) {
            quality = upgradeQualityForDPM(quality);
            quality = upgradeQualityForKeyStore(quality);
            return quality;
        }

        private int upgradeQualityForDPM(int quality) {
            // Compare min allowed password quality
            int minQuality = mDPM.getPasswordQuality(null);
            if (quality < minQuality) {
                quality = minQuality;
            }
            return quality;
        }

        private int upgradeQualityForKeyStore(int quality) {
            if (!mKeyStore.isEmpty()) {
                if (quality < CredentialStorage.MIN_PASSWORD_QUALITY) {
                    quality = CredentialStorage.MIN_PASSWORD_QUALITY;
                }
            }
            return quality;
        }

        /***
         * Disables preferences that are less secure than required quality.
         *
         * @param quality the requested quality.
         */
        private void disableUnusablePreferences(final int quality, MutableBoolean allowBiometric) {
            final PreferenceScreen entries = getPreferenceScreen();
            final boolean onlyShowFallback = getActivity().getIntent()
                    .getBooleanExtra(LockPatternUtils.LOCKSCREEN_BIOMETRIC_WEAK_FALLBACK, false);
            final boolean weakBiometricAvailable =
                    mChooseLockSettingsHelper.utils().isBiometricWeakInstalled();

            // INTEL_LPAL
            final boolean onlyShowVoiceFallback = getActivity().getIntent().getBooleanExtra(
                    LockPatternUtils.LOCKSCREEN_BIOMETRIC_VOICE_WEAK_FALLBACK, false);
            final boolean weakBiometricVoiceAvailable =
                    mChooseLockSettingsHelper.utils().isBiometricVoiceWeakInstalled();
            final boolean isVtsv = getActivity().getIntent().getBooleanExtra(VTSV_FLAG, false);
            final int operation = getActivity().getIntent().getIntExtra(
                    VTSV_OPERATION, VTSV_FALLBACK_OPERATION);

            boolean isVoiceSelectable = true;
            if (isVtsv && operation == VTSV_DISABLE_VU_OPERATION) {
                isVoiceSelectable = false;
            }

            // if there are multiple users, disable "None" setting
            UserManager mUm = (UserManager) getSystemService(Context.USER_SERVICE);
            List<UserInfo> users = mUm.getUsers(true);
            final boolean singleUser = users.size() == 1;

            for (int i = entries.getPreferenceCount() - 1; i >= 0; --i) {
                Preference pref = entries.getPreference(i);
                if (pref instanceof PreferenceScreen) {
                    final String key = ((PreferenceScreen) pref).getKey();
                    boolean enabled = true;
                    boolean visible = true;

                    // INTEL_LPAL
                    if (KEY_UNLOCK_SET_VOICE.equals(key)) {
                        enabled = (DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_VOICE_WEAK
                                > quality);
                        visible = weakBiometricVoiceAvailable;
                        if (!isVoiceSelectable) {
                            Log.d(INTEL_LPAL_TAG, "remove voice unlock preference");
                            // voice unlock
                            entries.removePreference(pref);
                            continue;
                        }
                    } else {
                        if (KEY_UNLOCK_SET_OFF.equals(key)) {
                            enabled = quality <= DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
                            visible = singleUser; // don't show when there's more than 1 user
                        } else if (KEY_UNLOCK_SET_NONE.equals(key)) {
                            enabled = quality <= DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
                        } else if (KEY_UNLOCK_SET_BIOMETRIC_WEAK.equals(key)) {
                            enabled = quality <= DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_WEAK ||
                                    allowBiometric.value;
                            visible = weakBiometricAvailable; // If not available, then don't show it.
                        } else if (KEY_UNLOCK_SET_PATTERN.equals(key)) {
                            enabled = quality <= DevicePolicyManager.PASSWORD_QUALITY_SOMETHING;
                        } else if (KEY_UNLOCK_SET_PIN.equals(key)) {
                        enabled = quality <= DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX;
                        } else if (KEY_UNLOCK_SET_PASSWORD.equals(key)) {
                            enabled = quality <= DevicePolicyManager.PASSWORD_QUALITY_COMPLEX;
                        }
                    }


                    // INTEL_LPAL
                    if (!visible || (onlyShowVoiceFallback && !allowedForFallback(key))) {
                        Log.d(INTEL_LPAL_TAG, "voice unlock fallback, remove useless choice");
                        // voice unlock
                        entries.removePreference(pref);
                    } else {
                        if (!visible || (onlyShowFallback && !allowedForFallback(key))) {
                            entries.removePreference(pref);
                        }  else if (!enabled) {
                            pref.setSummary(R.string.unlock_set_unlock_disabled_summary);
                            pref.setEnabled(false);
                        }
                    }
                }
            }
        }

        private void updatePreferenceSummaryIfNeeded() {
            if (LockPatternUtils.isDeviceEncrypted()) {
                return;
            }

            if (AccessibilityManager.getInstance(getActivity()).getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK).isEmpty()) {
                return;
            }

            CharSequence summary = getString(R.string.secure_lock_encryption_warning);

            PreferenceScreen screen = getPreferenceScreen();
            final int preferenceCount = screen.getPreferenceCount();
            for (int i = 0; i < preferenceCount; i++) {
                Preference preference = screen.getPreference(i);
                switch (preference.getKey()) {
                    case KEY_UNLOCK_SET_PATTERN:
                    case KEY_UNLOCK_SET_PIN:
                    case KEY_UNLOCK_SET_PASSWORD: {
                        preference.setSummary(summary);
                    } break;
                }
            }
        }

        /**
         * Check whether the key is allowed for fallback (e.g. bio sensor). Returns true if it's
         * supported as a backup.
         *
         * @param key
         * @return true if allowed
         */
        private boolean allowedForFallback(String key) {
            return KEY_UNLOCK_BACKUP_INFO.equals(key)  ||
                    KEY_UNLOCK_SET_PATTERN.equals(key) || KEY_UNLOCK_SET_PIN.equals(key);
        }

        private Intent getBiometricSensorIntent() {
            Intent fallBackIntent = new Intent().setClass(getActivity(),
                    ChooseLockGeneric.InternalActivity.class);
            fallBackIntent.putExtra(LockPatternUtils.LOCKSCREEN_BIOMETRIC_WEAK_FALLBACK, true);
            fallBackIntent.putExtra(CONFIRM_CREDENTIALS, false);
            fallBackIntent.putExtra(EXTRA_SHOW_FRAGMENT_TITLE,
                    R.string.backup_lock_settings_picker_title);

            boolean showTutorial = ALWAY_SHOW_TUTORIAL ||
                    !mChooseLockSettingsHelper.utils().isBiometricWeakEverChosen();
            Intent intent = new Intent();
            intent.setClassName("com.android.facelock", "com.android.facelock.SetupIntro");
            intent.putExtra("showTutorial", showTutorial);
            // INTEL_LPAL start
            PendingIntent pending = PendingIntent.getActivity(getActivity(), 0, fallBackIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT);
            // INTEL_LPAL end
            intent.putExtra("PendingIntent", pending);
            return intent;
        }

        /**
         * INTEL_LPAL: get intent of voice setup activity
         */
        private Intent getBiometricVoiceSensorIntent() {
            Log.d(INTEL_LPAL_TAG, "getBiometricVoiceSensorIntent");
            Intent fallBackIntent = new Intent().setClass(getActivity(),
                    ChooseLockGeneric.InternalActivity.class);
            fallBackIntent.putExtra(LockPatternUtils.LOCKSCREEN_BIOMETRIC_VOICE_WEAK_FALLBACK,
                    true);
            fallBackIntent.putExtra(CONFIRM_CREDENTIALS, false);
            fallBackIntent.putExtra(EXTRA_SHOW_FRAGMENT_TITLE,
                    R.string.backup_lock_settings_picker_title);

            boolean showTutorial = ALWAY_SHOW_TUTORIAL ||
                    !mChooseLockSettingsHelper.utils().isBiometricVoiceWeakEverChosen();
            Intent intent = new Intent();
            intent.setClassName("com.intel.voiceenrollment",
                    "com.intel.voiceenrollment.ui.SetupWelcome");
            intent.putExtra("showTutorial", showTutorial);
            PendingIntent pending = PendingIntent.getActivity(getActivity(), 0,
                    fallBackIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            intent.putExtra("PendingIntent", pending);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return intent;
        }

        /**
         * Invokes an activity to change the user's pattern, password or PIN based on given quality
         * and minimum quality specified by DevicePolicyManager. If quality is
         * {@link DevicePolicyManager#PASSWORD_QUALITY_UNSPECIFIED}, password is cleared.
         *
         * @param quality the desired quality. Ignored if DevicePolicyManager requires more security
         * @param disabled whether or not to show LockScreen at all. Only meaningful when quality is
         * {@link DevicePolicyManager#PASSWORD_QUALITY_UNSPECIFIED}
         */
        void updateUnlockMethodAndFinish(int quality, boolean disabled) {
            // Sanity check. We should never get here without confirming user's existing password.
            if (!mPasswordConfirmed) {
                throw new IllegalStateException("Tried to update password without confirming it");
            }

            final boolean isFallback = getActivity().getIntent()
                .getBooleanExtra(LockPatternUtils.LOCKSCREEN_BIOMETRIC_WEAK_FALLBACK, false);

            quality = upgradeQuality(quality, null);

            // INTEL_LPAL start
            boolean isVoiceFallback = getActivity().getIntent()
                    .getBooleanExtra(LockPatternUtils.LOCKSCREEN_BIOMETRIC_VOICE_WEAK_FALLBACK,
                    false);

            Log.d(INTEL_LPAL_TAG, "isVoiceFallback:" + isVoiceFallback);

            if (quality == DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_VOICE_WEAK) {
                Log.d(INTEL_LPAL_TAG, "quality is PASSWORD_QUALITY_BIOMETRIC_VOICE_WEAK");
                Intent intent = getBiometricVoiceSensorIntent();
                mFinishPending = true;
                startActivity(intent);
                return;
            }
            // INTEL_LPAL end

            if (quality >= DevicePolicyManager.PASSWORD_QUALITY_NUMERIC) {
                int minLength = mDPM.getPasswordMinimumLength(null);
                if (minLength < MIN_PASSWORD_LENGTH) {
                    minLength = MIN_PASSWORD_LENGTH;
                }
                final int maxLength = mDPM.getPasswordMaximumLength(quality);
                Intent intent = ChooseLockPassword.createIntent(getActivity(), quality, isFallback,
                        minLength, maxLength, mRequirePassword, false /* confirm credentials */);

                // INTEL_LPAL start
                if (isVoiceFallback) {
                    Log.d(INTEL_LPAL_TAG, "fallback from voice unlock");
                    Intent voiceIntent = ChooseLockPassword.createIntentForLPAL(getActivity(),
                            quality, isVoiceFallback, minLength, maxLength,
                            mRequirePassword, false);
                    startActivityForResult(voiceIntent, FALLBACK_REQUEST);
                    return;
                }
                // INTEL_LPAL end

                if (isFallback) {
                    startActivityForResult(intent, FALLBACK_REQUEST);
                    return;
                } else {
                    mFinishPending = true;
                    intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                    startActivity(intent);
                }
            } else if (quality == DevicePolicyManager.PASSWORD_QUALITY_SOMETHING) {
                Intent intent = ChooseLockPattern.createIntent(getActivity(),
                        isFallback, mRequirePassword, false /* confirm credentials */);

                // INTEL_LPAL start
                if (isVoiceFallback) {
                    Log.d(INTEL_LPAL_TAG, "fallback from voice unlock");
                    Intent voiceIntent = ChooseLockPattern.createIntentForLPAL(getActivity(),
                            isVoiceFallback, mRequirePassword, false /* confirm credentials */);
                    startActivityForResult(voiceIntent, FALLBACK_REQUEST);
                    return;
                }
                // INTEL_LPAL end

                if (isFallback) {
                    startActivityForResult(intent, FALLBACK_REQUEST);
                    return;
                } else {
                    mFinishPending = true;
                    intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                    startActivity(intent);
                }
            } else if (quality == DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_WEAK) {
                Intent intent = getBiometricSensorIntent();
                mFinishPending = true;
                startActivity(intent);
            } else if (quality == DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED) {
                mChooseLockSettingsHelper.utils().clearLock(false);
                mChooseLockSettingsHelper.utils().setLockScreenDisabled(disabled);
                getActivity().setResult(Activity.RESULT_OK);
                finish();
            } else {
                finish();
            }
        }

        @Override
        protected int getHelpResource() {
            return R.string.help_url_choose_lockscreen;
        }

    }
}
