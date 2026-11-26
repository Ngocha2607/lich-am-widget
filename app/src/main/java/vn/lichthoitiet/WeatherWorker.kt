package vn.lichthoitiet

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Tải thời tiết ở nền rồi vẽ lại widget. */
class WeatherWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        Reminders.schedule(applicationContext) // tự đặt lại báo thức nếu hệ thống đã hủy
        try { CalendarSync.autoImport(applicationContext) } catch (e: Exception) { }   // tự lấy sự kiện từ lịch Google nếu đã bật
        return try {
            WeatherRepo.refresh(applicationContext)
            LichWidget.updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            // Vẫn vẽ lại widget để lịch âm đúng ngày dù không có mạng
            LichWidget.updateAll(applicationContext)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PERIODIC = "weather_periodic"
        private const val ONCE = "weather_once"

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Cập nhật định kỳ 3 giờ một lần (khoảng 3-4 lần trong giờ thức). */
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<WeatherWorker>(3, TimeUnit.HOURS)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun refreshNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<WeatherWorker>().setConstraints(network).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(ONCE, ExistingWorkPolicy.KEEP, req)
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC)
        }
    }
}
