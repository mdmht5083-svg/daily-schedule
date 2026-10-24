package com.example.dailyschedule

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks")
data class ScheduleTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startMinute: Int,
    val durationMinutes: Int,
    val reminderEnabled: Boolean = true,
    val enabled: Boolean = true
)

@Entity(tableName = "completions", primaryKeys = ["taskId", "date"])
data class TaskCompletion(
    val taskId: Long,
    val date: String,
    val completed: Boolean,
    val actualMinutes: Int = 0
)

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM tasks ORDER BY startMinute ASC")
    fun observeTasks(): Flow<List<ScheduleTask>>

    @Query("SELECT * FROM tasks ORDER BY startMinute ASC")
    suspend fun allTasks(): List<ScheduleTask>

    @Insert
    suspend fun insertTask(task: ScheduleTask): Long

    @Update
    suspend fun updateTask(task: ScheduleTask)

    @Delete
    suspend fun deleteTask(task: ScheduleTask)

    @Query("SELECT * FROM completions WHERE date = :date")
    fun observeCompletions(date: String): Flow<List<TaskCompletion>>

    @Query("SELECT * FROM completions")
    suspend fun allCompletions(): List<TaskCompletion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCompletion(completion: TaskCompletion)

    @Query("DELETE FROM tasks")
    suspend fun clearTasks()

    @Query("DELETE FROM completions")
    suspend fun clearCompletions()

    @Transaction
    suspend fun replaceAll(tasks: List<ScheduleTask>, completions: List<TaskCompletion>) {
        clearTasks()
        clearCompletions()
        val idMap = mutableMapOf<Long, Long>()
        tasks.forEach { task ->
            val newId = insertTask(task.copy(id = 0))
            idMap[task.id] = newId
        }
        completions.forEach { completion ->
            val newTaskId = idMap[completion.taskId]
            if (newTaskId != null) putCompletion(completion.copy(taskId = newTaskId))
        }
    }
}

@Database(entities = [ScheduleTask::class, TaskCompletion::class], version = 1, exportSchema = false)
abstract class ScheduleDatabase : RoomDatabase() {
    abstract fun dao(): ScheduleDao

    companion object {
        @Volatile private var INSTANCE: ScheduleDatabase? = null

        fun get(context: Context): ScheduleDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScheduleDatabase::class.java,
                    "daily_schedule.db"
                ).build().also { INSTANCE = it }
            }
    }
}
