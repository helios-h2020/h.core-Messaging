package eu.h2020.helios_social.core.messaging.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

/**
 * Helios message store database using Room persistence library (SQLite-based).
 */
@Database(entities = {HeliosData.class}, version = 3, exportSchema = false)
public abstract class HeliosDatabase extends RoomDatabase {
    public abstract HeliosDataDao heliosDataDao();
}
