/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.backup;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import androidx.annotation.NonNull;
import dalvik.system.VMRuntime;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.types.PrivilegedFile;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public final class MetadataManager implements Closeable {
    public static final String META_FILE = "meta.am.v1";

    // For an extended documentation, see https://github.com/MuntashirAkon/AppManager/issues/30
    // All the attributes must be non-null
    public static class Metadata {
        public String label;  // label
        public String packageName;  // package_name
        public String versionName;  // version_name
        public long versionCode;  // version_code
        public String[] dataDirs;  // data_dirs
        public boolean isSystem;  // is_system
        public boolean isSplitApk;  // is_split_apk
        public String[] splitConfigs;  // split_configs
        public String[] splitNames;  // split_names
        public boolean hasRules;  // has_rules
        public long backupTime;  // backup_time
        public String[] certSha256Checksum;  // cert_sha256_checksum
        public String sourceSha256Checksum;  // source_dir_sha256_checksum
        public String[] dataSha256Checksum;  // data_dirs_sha256_checksum
        public int mode = 0;  // mode
        public int version = 1;  // version
        public String apkName;  // apk_name
        public String instructionSet = VMRuntime.getInstructionSet(Build.SUPPORTED_ABIS[0]);  // instruction_set
        public BackupFlags flags;  // flags
        public int userHandle;  // user_handle
        @TarUtils.TarType
        public String tarType;  // tar_type
        public boolean keyStore;  // key_store
    }

    private static MetadataManager metadataManager;

    public static MetadataManager getInstance(String packageName) {
        if (metadataManager == null) metadataManager = new MetadataManager(packageName);
        if (!metadataManager.packageName.equals(packageName)) {
            metadataManager.close();
            metadataManager = new MetadataManager(packageName);
        }
        return metadataManager;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean hasMetadata(String packageName) {
        PrivilegedFile backupPath = new PrivilegedFile(BackupFiles.getPackagePath(packageName),
                String.valueOf(Users.getCurrentUserHandle()));
        return new PrivilegedFile(backupPath, META_FILE).exists();
    }

    @Override
    public void close() {
    }

    private @NonNull
    String packageName;
    private Metadata metadata;
    private AppManager appManager;

    MetadataManager(@NonNull String packageName) {
        this.packageName = packageName;
        this.appManager = AppManager.getInstance();
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    synchronized public void readMetadata(@NonNull BackupFiles.BackupFile backupFile)
            throws JSONException {
        String metadata = Utils.getFileContent(backupFile.getMetadataFile());
        if (TextUtils.isEmpty(metadata)) throw new JSONException("Empty JSON string");
        JSONObject rootObject = new JSONObject(metadata);
        this.metadata = new Metadata();
        this.metadata.label = rootObject.getString("label");
        this.metadata.packageName = rootObject.getString("package_name");
        this.metadata.versionName = rootObject.getString("version_name");
        this.metadata.versionCode = rootObject.getLong("version_code");
        this.metadata.dataDirs = getArrayFromJSONArray(rootObject.getJSONArray("data_dirs"));
        this.metadata.isSystem = rootObject.getBoolean("is_system");
        this.metadata.isSplitApk = rootObject.getBoolean("is_split_apk");
        this.metadata.splitConfigs = getArrayFromJSONArray(rootObject.getJSONArray("split_configs"));
        this.metadata.splitNames = getArrayFromJSONArray(rootObject.getJSONArray("split_names"));
        this.metadata.hasRules = rootObject.getBoolean("has_rules");
        this.metadata.backupTime = rootObject.getLong("backup_time");
        this.metadata.certSha256Checksum = getArrayFromJSONArray(rootObject.getJSONArray("cert_sha256_checksum"));
        this.metadata.sourceSha256Checksum = rootObject.getString("source_sha256_checksum");
        this.metadata.dataSha256Checksum = getArrayFromJSONArray(rootObject.getJSONArray("data_sha256_checksum"));
        this.metadata.mode = rootObject.getInt("mode");
        this.metadata.version = rootObject.getInt("version");
        this.metadata.apkName = rootObject.getString("apk_name");
        this.metadata.instructionSet = rootObject.getString("instruction_set");
        this.metadata.flags = new BackupFlags(rootObject.getInt("flags"));
        this.metadata.userHandle = rootObject.getInt("user_handle");
        this.metadata.tarType = rootObject.getString("tar_type");
        this.metadata.keyStore = rootObject.getBoolean("key_store");
    }

    synchronized public void writeMetadata(@NonNull BackupFiles.BackupFile backupFile)
            throws IOException, JSONException {
        if (metadata == null) throw new RuntimeException("Metadata is not set.");
        File metadataFile = backupFile.getMetadataFile();
        try (FileOutputStream fileOutputStream = new FileOutputStream(metadataFile)) {
            JSONObject rootObject = new JSONObject();
            rootObject.put("label", metadata.label);
            rootObject.put("package_name", metadata.packageName);
            rootObject.put("version_name", metadata.versionName);
            rootObject.put("version_code", metadata.versionCode);
            rootObject.put("data_dirs", getJSONArrayFromArray(metadata.dataDirs));
            rootObject.put("is_system", metadata.isSystem);
            rootObject.put("is_split_apk", metadata.isSplitApk);
            rootObject.put("split_configs", getJSONArrayFromArray(metadata.splitConfigs));
            rootObject.put("split_names", getJSONArrayFromArray(metadata.splitNames));
            rootObject.put("has_rules", metadata.hasRules);
            rootObject.put("backup_time", metadata.backupTime);
            rootObject.put("cert_sha256_checksum", getJSONArrayFromArray(metadata.certSha256Checksum));
            rootObject.put("source_sha256_checksum", metadata.sourceSha256Checksum);
            rootObject.put("data_sha256_checksum", getJSONArrayFromArray(metadata.dataSha256Checksum));
            rootObject.put("mode", metadata.mode);
            rootObject.put("version", metadata.version);
            rootObject.put("apk_name", metadata.apkName);
            rootObject.put("instruction_set", metadata.instructionSet);
            rootObject.put("flags", metadata.flags.getFlags());
            rootObject.put("user_handle", metadata.userHandle);
            rootObject.put("tar_type", metadata.tarType);
            rootObject.put("key_store", metadata.keyStore);
            fileOutputStream.write(rootObject.toString().getBytes());
        }
    }

    @NonNull
    private static JSONArray getJSONArrayFromArray(@NonNull final String[] stringArray) {
        JSONArray jsonArray = new JSONArray();
        for (String string : stringArray) jsonArray.put(string);
        return jsonArray;
    }

    @NonNull
    private static String[] getArrayFromJSONArray(@NonNull final JSONArray jsonArray)
            throws JSONException {
        String[] stringArray = new String[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); ++i) stringArray[i] = (String) jsonArray.get(i);
        return stringArray;
    }

    public Metadata setupMetadata(@NonNull PackageInfo packageInfo,
                                  int userHandle,
                                  @NonNull BackupFlags requestedFlags) {
        PackageManager pm = appManager.getPackageManager();
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        metadata = new Metadata();
        metadata.flags = requestedFlags;
        metadata.userHandle = userHandle;
        metadata.tarType = TarUtils.TAR_GZIP;  // FIXME: Load from user prefs
        metadata.keyStore = BackupUtils.hasKeyStore(applicationInfo.uid);
        metadata.label = applicationInfo.loadLabel(pm).toString();
        metadata.packageName = packageName;
        metadata.versionName = packageInfo.versionName;
        metadata.versionCode = PackageUtils.getVersionCode(packageInfo);
        metadata.apkName = new File(applicationInfo.sourceDir).getName();
        if (requestedFlags.backupData()) {
            // FIXME(10/7/20): External data directory is not respecting userHandle
            metadata.dataDirs = PackageUtils.getDataDirs(applicationInfo,
                    requestedFlags.backupExtData(), requestedFlags.backupMediaObb());
        }
        metadata.dataDirs = ArrayUtils.defeatNullable(metadata.dataDirs);
        metadata.isSystem = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        metadata.isSplitApk = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            metadata.splitConfigs = applicationInfo.splitNames;
            if (metadata.splitConfigs != null) {
                metadata.isSplitApk = true;
                metadata.splitNames = new String[metadata.splitConfigs.length];
                for (int i = 0; i < applicationInfo.splitPublicSourceDirs.length; ++i) {
                    metadata.splitNames[i] = new File(applicationInfo.splitPublicSourceDirs[i]).getName();
                }
            }
        }
        metadata.splitConfigs = ArrayUtils.defeatNullable(metadata.splitConfigs);
        metadata.splitNames = ArrayUtils.defeatNullable(metadata.splitNames);
        metadata.hasRules = false;
        if (requestedFlags.backupRules()) {
            try (ComponentsBlocker cb = ComponentsBlocker.getInstance(packageName)) {
                metadata.hasRules = cb.entryCount() > 0;
            }
        }
        metadata.backupTime = 0;
        metadata.certSha256Checksum = PackageUtils.getSigningCertSha256Checksum(packageInfo);
        // Initialize checksums
        metadata.sourceSha256Checksum = "";
        metadata.dataSha256Checksum = new String[metadata.dataDirs.length];
        return metadata;
    }
}
