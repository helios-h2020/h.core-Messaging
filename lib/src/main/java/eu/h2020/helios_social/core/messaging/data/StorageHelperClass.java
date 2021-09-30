package eu.h2020.helios_social.core.messaging.data;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import eu.h2020.helios_social.core.messaging.HeliosStorageHelper;
import eu.h2020.helios_social.core.messaging.MessagingConstants;
import eu.h2020.helios_social.core.storage.HeliosStorageUtils;

/**
 * Helper class implementing data storage functionality defined in {@link HeliosStorageHelper}.
 * Uses {@link HeliosStorageUtils} for actual operations.
 */
public class StorageHelperClass implements HeliosStorageHelper {
    private static final String TAG = "StorageHelperClass";
    private Context mContext;
    private int mMediaFileCount = 0;

    /**
     * Constructor for the class.
     * @param context {@link Context} to be used in file operations.
     */
    public StorageHelperClass(Context context) {
        Log.d(TAG, "StorageHelperClass()");
        mContext = context;
    }

    @Override
    public byte[] getFileBytesInternal(final String fileName) {
        return HeliosStorageUtils.getFileBytes(mContext.getFilesDir(), fileName);
    }

    @Override
    public boolean deleteFileInternal(String fileName) {
        return HeliosStorageUtils.deleteFile(mContext.getFilesDir(), fileName);
    }

    @Override
    public boolean saveFileExternal(byte[] data, String fileName) {
        return HeliosStorageUtils.saveFile(data, mContext.getExternalFilesDir(null), fileName);
    }

    @Override
    public String generateFileNameByExtension(final String originalFileName) {
        String fileName = "";
        String fileExt = getFileExt(originalFileName);
        if (!TextUtils.isEmpty(fileExt)) {
            // Generate a filename with defined datetime pattern and given fileExt extension.
            SimpleDateFormat sdf = new SimpleDateFormat(MessagingConstants.HELIOS_RECEIVED_DATETIME_PATTERN, Locale.getDefault());
            String datetimeString = sdf.format(new Date());
            fileName = MessagingConstants.HELIOS_RECEIVED_FILENAME_START + datetimeString + "-" + mMediaFileCount + fileExt;
            mMediaFileCount++;
        }

        return fileName;
    }

    private String getFileExt(String fileName) {
        //TODO refactor check
        String fileExt = "";
        if (TextUtils.isEmpty(fileName)) {
            Log.e(TAG, "FileName empty or null.");
            return fileExt;
        }

        if (fileName.endsWith(".mp4")) {
            fileExt = ".mp4";
        } else if (fileName.endsWith(".jpg")) {
            fileExt = ".jpg";
        } else if (fileName.endsWith(".png")) {
            fileExt = ".png";
        } else {
            Log.e(TAG, "Unknown MimeType/file extension.");
        }
        return fileExt;
    }

}
