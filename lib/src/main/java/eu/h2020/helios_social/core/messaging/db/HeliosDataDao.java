package eu.h2020.helios_social.core.messaging.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * Data access object (DAO) class to access Room persistence library message database
 */
@Dao
public interface HeliosDataDao {

    @Insert
    void addMessages(HeliosData... data);

    @Query("SELECT * FROM  messages ORDER BY Milliseconds")
    List<HeliosData> dumpDB();

    @Query("SELECT * FROM  messages WHERE Topic = :topic ORDER BY Milliseconds")
    List<HeliosData> loadMessages(String topic);

    @Query("SELECT * FROM  messages WHERE Topic = :topic AND Milliseconds > :since ORDER BY Milliseconds")
    List<HeliosData> loadMessages(String topic, long since);

    @Query("SELECT DISTINCT Topic FROM messages ORDER BY Topic ASC")
    List<String> getTopics();

    @Query("DELETE FROM messages WHERE Milliseconds < :milliseconds")
    void deleteExpiredEntries(long milliseconds);

    @Query("UPDATE messages SET Received = :fieldval WHERE Message_UUID = :msgUuid")
    void setReceivedField(String msgUuid, boolean fieldval);
}
