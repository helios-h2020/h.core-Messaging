package eu.h2020.helios_social.core.messaging;

/**
 * An interface for HELIOS file operations.
 */
public interface HeliosStorageHelper {
   /**
    * Get file from internal storage with a file name.
    *
    * @param fileName filename for the file.
    * @return file as byte array.
    */
   byte[] getFileBytesInternal(String fileName);

   /**
    * Directly delete an internal file.
    *
    * @param fileName file name to be deleted.
    * @return boolean value whether delete was successful.
    */
   boolean deleteFileInternal(String fileName);

   /**
    * Directly save file to external storage.
    *
    * @param data byte array to be saved.
    * @param fileName target file name to be saved.
    * @return boolean value whether save was successful.
    */
   boolean saveFileExternal(byte[] data, String fileName);

   /**
    * Generate a file name based on extension to provide a new file name that can be safely used
    * to storage a file. For example "pic.jpg", "pic.png", "video.mp4" should return a new name like
    * {@link MessagingConstants#HELIOS_RECEIVED_FILENAME_START} + datetimeString + "-" + mMediaFileCount + fileExt;
    *
    * @param fileName file name to be used to generate a new file name.
    * @return new file name based on given extension.
    */
   String generateFileNameByExtension(String fileName);
}