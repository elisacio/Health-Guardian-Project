package com.example.applisante

import android.annotation.SuppressLint
import android.content.Context
import androidx.room.*
import com.opencsv.CSVReader
import org.mindrot.jbcrypt.BCrypt
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.Locale.getDefault


enum class EnumSleepingStage {
    DEEP, LIGHT, AWAKE, REM, NOSLEEP
}

enum class EnumSex {
    MALE, FEMALE, OTHER
}

@Entity(
    tableName = "user_data",
    foreignKeys = [ForeignKey(
        entity = User::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["userId"])]
)
data class UserData(
    @PrimaryKey(autoGenerate = true) val dataId: Int = 0,
    val userId: Int,
    val isoDate: String,
    val heart_rate: Int,
    val GarminStressLevel: Int,
    val ZCC: Int,
    val SleepingStage: EnumSleepingStage,
    val StageDuration: Int,
    val SleepScore: Int,
    val RestingHeartRate: Int,
    val pNN50: Float,
    val SD2: Float,
    val CycleID: Int,
    val DayInCycle: Int,
    val BasaleTemperature: Float,
    val IsOnPeriod: Boolean,
    val HasAnsweredPeriodQ: Boolean = false,
    val AvgCycleLength: Float,
    val AvgPeriodLength: Float,
    val note: String = "",
    val isExcluded: Boolean = false,
    val TAT: Float,
    val Energy: Float,
    val ActivityScore: Float = 0f,
    val Steps: Int,
    val Calories: Float,
    val RealStress: Float = 0f,
    val DepressionScore: Float = 0f,
    val AnxietyScore: Float = 0f
)

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val email: String = "",
    val passwordHash: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val age: Int = 0,
    val height: Float = 0.0f,
    val weight: Float = 0.0f,
    val menstruated: Boolean = false,
    val sex: EnumSex = EnumSex.OTHER,
    val phq9Score: Int = -1,
    val gad7Score : Int = -1,
    val lastPeriodDate: String = "",      // Format "YYYY-MM-DD"
    val initialAvgCycleLength: Int = 28,
    val initialAvgPeriodLength: Int = 5,
    val isCycleRegular: Boolean = true,
    val dataFileName: String = "data_final_with_cycles.csv"
)

class Converters {
    @TypeConverter
    fun fromSleepingStage(stage: EnumSleepingStage): String = stage.name

    @TypeConverter
    fun toSleepingStage(stage: String): EnumSleepingStage = EnumSleepingStage.valueOf(stage)

    @TypeConverter
    fun fromSex(sex: EnumSex): String = sex.name

    @TypeConverter
    fun toSex(sex: String): EnumSex = EnumSex.valueOf(sex)
}

@Dao
interface UserDao {

    @Insert
    suspend fun insert(user: User): Long

    @Update
    suspend fun update(user: User)

    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun getByEmail(email: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserData(userData: List<UserData>)

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getFirstUser(): User?

    @Query("SELECT * FROM user_data WHERE userId = :userId")
    suspend fun getUserData(userId: Int): List<UserData>

    @Query("SELECT * FROM user_data WHERE userId = :userId ORDER BY isoDate DESC LIMIT 1")
    suspend fun getLatestUserData(userId: Int): UserData?

    @Query("SELECT * FROM user_data WHERE userId = :userId AND isoDate = :isoDate LIMIT 1")
    suspend fun getUserDataByDate(userId: Int, isoDate: String): UserData?

    @Query("SELECT * FROM user_data WHERE userId = :userId AND isoDate >= :startDate AND isoDate <= :endDate")
    suspend fun getUserDataInRange(userId: Int, startDate: String, endDate: String): List<UserData>

    @Query("DELETE FROM user_data WHERE userId = :userId")
    suspend fun deleteUserData(userId: Int)

    @Query("UPDATE user_data SET IsOnPeriod = :isOnPeriod WHERE dataId = :dataId")
    suspend fun updatePeriodStatus(dataId: Int, isOnPeriod: Boolean)

    @Query("UPDATE user_data SET HasAnsweredPeriodQ = :hasAnsweredPeriodQ WHERE dataId = :dataId")
    suspend fun updatePeriodQStatus(dataId: Int, hasAnsweredPeriodQ: Boolean)

    @Query("UPDATE user_data SET note = :note WHERE userId = :userId AND CycleID = :cycleId")
    suspend fun updateCycleNote(userId: Int, cycleId: Int, note: String)

    @Query("UPDATE user_data SET isExcluded = :isExcluded WHERE userId = :userId AND CycleID = :cycleId")
    suspend fun updateCycleExclusion(userId: Int, cycleId: Int, isExcluded: Boolean)
}


@Database(entities = [User::class, UserData::class], version = 18, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

object PasswordUtils {
    fun hash(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt())
    fun verify(password: String, hash: String): Boolean = BCrypt.checkpw(password, hash)
}

fun verifDateCSV(context: Context, isoDateMax: String, fileName: String): Boolean {
    context.assets.open("data/$fileName").use { inputStream ->
        InputStreamReader(inputStream).use { streamReader ->
            CSVReader(streamReader).use { csvReader ->
                csvReader.readNext()
                return csvReader.any { row -> row.isNotEmpty() && row[0] == isoDateMax }
            }
        }
    }
}

data class CsvUserData(
    val isoDate: String,
    val heart_rate: Int,
    val GarminStressLevel: Int,
    val ZCC: Int,
    val SleepingStage: EnumSleepingStage,
    val StageDuration: Int,
    val SleepScore: Int,
    val RestingHeartRate: Int,
    val pNN50: Float,
    val SD2: Float,
    val CycleID: Int,
    val DayInCycle: Int,
    val BasaleTemperature: Float,
    val IsOnPeriod: Boolean,
    val HasAnsweredPeriodQ: Boolean,
    val AvgCycleLength: Float,
    val AvgPeriodLength: Float,
    val note: String,
    val isExcluded: Boolean,
    val TAT: Float,
    val Energy: Float,
    val ActivityScore: Float,
    val Steps: Int,
    val Calories: Float,
    val RealStress: Float,
    val DepressionScore: Float,
    val AnxietyScore: Float
)

private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

fun readCsvFromAssets(context: Context, isoDateMax: String, fileName: String): Pair<List<CsvUserData>, String> {
    var bonIsoDate = isoDateMax
    var currentDateTime = try {
        LocalDateTime.parse(isoDateMax, isoFormatter)
    } catch (e: Exception) {
        // Fallback or rethrow if format is wrong
        LocalDateTime.now()
    }

    while (!(verifDateCSV(context, bonIsoDate, fileName))) {
        currentDateTime = currentDateTime.plusMinutes(1)
        bonIsoDate = currentDateTime.format(isoFormatter)
    }

    val result = LinkedList<CsvUserData>()
    val reader = CSVReader(InputStreamReader(context.assets.open("data/$fileName")))
    val data = reader.readAll()
    reader.close()

    for (row in data) {
        if (row.isEmpty() || row[0] == "isoDate") continue
        val isoDate = row[0]
        if (isoDate <= bonIsoDate) {
            result.add(CsvUserData(
                isoDate           = isoDate,
                heart_rate        = row[1].toFloatOrNull()?.toInt() ?: 0,
                GarminStressLevel = row[2].toFloatOrNull()?.toInt() ?: 0,
                ZCC               = row[3].toFloatOrNull()?.toInt() ?: 0,
                SleepingStage     = try { EnumSleepingStage.valueOf(row[4].uppercase(getDefault())) }
                catch (ex: IllegalArgumentException) { EnumSleepingStage.NOSLEEP },
                StageDuration     = row[5].toFloatOrNull()?.toInt() ?: 0,
                SleepScore        = row[6].toFloatOrNull()?.toInt() ?: 0,
                RestingHeartRate  = row[7].toFloatOrNull()?.toInt() ?: 0,
                pNN50             = row[8].toFloatOrNull() ?: -1.0f,
                SD2               = row[9].toFloatOrNull() ?: -1.0f,
                CycleID           = row[10].toFloatOrNull()?.toInt() ?: -1,
                DayInCycle        = row[11].toFloatOrNull()?.toInt() ?: -1,
                BasaleTemperature = row[12].toFloatOrNull() ?: -1.0f,
                IsOnPeriod = row[13].trim().equals("true", ignoreCase = true) || row[13].trim() == "1" || row[13].trim() == "1.0",
                HasAnsweredPeriodQ = false,
                AvgCycleLength    = row[14].toFloatOrNull() ?: -1.0f,
                AvgPeriodLength   = row[15].toFloatOrNull() ?: -1.0f,
                note              = "",
                isExcluded        = false,
                TAT               = row[16].toFloatOrNull() ?: 0.0f,
                Energy            = row[17].toFloatOrNull() ?: 0.0f,
                ActivityScore     = if (row.size > 18) row[18].toFloatOrNull() ?: 0.0f else 0.0f,
                Steps             = if (row.size > 19) row[19].toFloatOrNull()?.toInt() ?: 0 else 0,
                Calories          = if (row.size > 20) row[20].toFloatOrNull() ?: 0.0f else 0.0f,
                RealStress        = if (row.size > 21) row[21].toFloatOrNull() ?: 0.0f else 0.0f,
                DepressionScore   = if (row.size > 22) row[22].toFloatOrNull() ?: 0.0f else 0.0f,
                AnxietyScore      = if (row.size > 23) row[23].toFloatOrNull() ?: 0.0f else 0.0f
            ))
        }
    }
    return Pair(result.sortedBy { it.isoDate }, bonIsoDate)
}



fun readCsvFromAssets_dict(context: Context, isoDateMax: String, fileName: String): HashMap<String, CsvUserData> {
    val (list, _) = readCsvFromAssets(context, isoDateMax, fileName)
    return HashMap(list.associateBy { it.isoDate })
}

fun readCsvFromAssets_list(context: Context, isoDateMax: String, fileName: String): List<CsvUserData> {
    val (list, _) = readCsvFromAssets(context, isoDateMax, fileName)
    return list
}


private fun CsvUserData.toUserData(userId: Int) = UserData(
    userId            = userId,
    isoDate           = isoDate,
    heart_rate        = heart_rate,
    GarminStressLevel = GarminStressLevel,
    ZCC               = ZCC,
    SleepingStage     = SleepingStage,
    StageDuration     = StageDuration,
    SleepScore        = SleepScore,
    RestingHeartRate  = RestingHeartRate,
    pNN50             = pNN50,
    SD2               = SD2,
    CycleID           = CycleID,
    DayInCycle        = DayInCycle,
    BasaleTemperature = BasaleTemperature,
    IsOnPeriod        = IsOnPeriod,
    HasAnsweredPeriodQ = false,
    AvgCycleLength    = AvgCycleLength,
    AvgPeriodLength   = AvgPeriodLength,
    note              = "",
    isExcluded        = false,
    TAT               = TAT,
    Energy            = Energy,
    ActivityScore     = ActivityScore,
    Steps             = Steps,
    Calories          = Calories,
    RealStress        = RealStress,
    DepressionScore   = DepressionScore,
    AnxietyScore      = AnxietyScore
)

suspend fun forward_week(context: Context, user: User, isoDateBase: String, db: AppDatabase) {
    val currentDateTime = LocalDateTime.parse(isoDateBase, isoFormatter)
    val nextDateTime = currentDateTime.plusDays(7)
    val isoDateMax = nextDateTime.format(isoFormatter)

    val (list, lastDate) = readCsvFromAssets(context, isoDateMax, user.dataFileName)
    db.userDao().deleteUserData(user.id)
    db.userDao().insertUserData(list.map { it.toUserData(user.id) })
    SessionManager.day = lastDate
}

suspend fun forward_day(context: Context, user: User, isoDateBase: String, db: AppDatabase) {
    val currentDateTime = LocalDateTime.parse(isoDateBase, isoFormatter)
    val nextDateTime = currentDateTime.plusDays(1)
    val isoDateMax = nextDateTime.format(isoFormatter)
    
    val (list, lastDate) = readCsvFromAssets(context, isoDateMax, user.dataFileName)
    db.userDao().deleteUserData(user.id)
    db.userDao().insertUserData(list.map { it.toUserData(user.id) })
    SessionManager.day = lastDate
}

suspend fun forward_hour(context: Context, user: User, isoDateBase: String, db: AppDatabase) {
    val currentDateTime = LocalDateTime.parse(isoDateBase, isoFormatter)
    val nextDateTime = currentDateTime.plusHours(1)
    val isoDateMax = nextDateTime.format(isoFormatter)

    val (list, lastDate) = readCsvFromAssets(context, isoDateMax, user.dataFileName)
    db.userDao().deleteUserData(user.id)
    db.userDao().insertUserData(list.map { it.toUserData(user.id) })
    SessionManager.day = lastDate
}

suspend fun forward_minute(context: Context, user: User, isoDateBase: String, db: AppDatabase) {
    val currentDateTime = LocalDateTime.parse(isoDateBase, isoFormatter)
    val nextDateTime = currentDateTime.plusMinutes(1)
    val isoDateMax = nextDateTime.format(isoFormatter)

    val (list, lastDate) = readCsvFromAssets(context, isoDateMax, user.dataFileName)
    db.userDao().deleteUserData(user.id)
    db.userDao().insertUserData(list.map { it.toUserData(user.id) })
    SessionManager.day = lastDate
}
