package com.jb.ileviatime.data.repository

import android.content.Context
import android.util.Log
import com.jb.ileviatime.data.local.dao.GtfsStaticDao
import com.jb.ileviatime.data.local.entities.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.*
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GtfsStaticRepository @Inject constructor(
    private val dao: GtfsStaticDao,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) {

    private val prefs = context.getSharedPreferences("gtfs_prefs", Context.MODE_PRIVATE)
    private val STATIC_GTFS_URL = "https://transport.data.gouv.fr/resources/81995/download"
    private val USER_AGENT = "IleviaTime-Android-App"
    private val TAG = "GtfsStaticRepository"

    suspend fun shouldUpdate(): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(STATIC_GTFS_URL)
            .header("User-Agent", USER_AGENT)
            .head()
            .build()
        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext true // On force l'update si le HEAD échoue
            
            val lastModified = response.header("Last-Modified")
            val savedLastModified = prefs.getString("last_modified", "")
            lastModified != savedLastModified
        } catch (e: Exception) {
            true // En cas d'erreur, on tente quand même le téléchargement
        }
    }

    suspend fun downloadAndImport(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting GTFS download...")
        val request = Request.Builder()
            .url(STATIC_GTFS_URL)
            .header("User-Agent", USER_AGENT)
            .build()
        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Download failed: ${response.code}")
                return@withContext false
            }
            
            val lastModified = response.header("Last-Modified")
            val contentLength = response.header("Content-Length")
            val tempFile = File(context.cacheDir, "gtfs_static.zip")
            
            Log.d(TAG, "Downloading to temp file...")
            response.body?.byteStream()?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Importing from ZIP...")
            val success = importFromZip(tempFile)
            
            if (success) {
                Log.d(TAG, "Import successful, updating prefs.")
                val editor = prefs.edit()
                if (lastModified != null) editor.putString("last_modified", lastModified)
                if (contentLength != null) editor.putString("content_length", contentLength)
                editor.apply()
            }
            
            tempFile.delete()
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error in downloadAndImport", e)
            false
        }
    }

    private suspend fun importFromZip(zipFile: File): Boolean {
        val extractedDir = File(context.cacheDir, "gtfs_extracted")
        extractedDir.mkdirs()
        
        try {
            Log.d(TAG, "Extracting files...")
            FileInputStream(zipFile).use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry = zis.getNextEntry()
                    while (entry != null) {
                        if (entry.name in listOf("routes.txt", "trips.txt", "stops.txt", "stop_times.txt")) {
                            val outFile = File(extractedDir, entry.name)
                            FileOutputStream(outFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.getNextEntry()
                    }
                }
            }

            Log.d(TAG, "Clearing database...")
            dao.clearAll()

            Log.d(TAG, "Parsing and inserting entities...")
            val routesFile = File(extractedDir, "routes.txt")
            if (routesFile.exists()) parseAndInsertRoutes(routesFile)
            
            val tripsFile = File(extractedDir, "trips.txt")
            if (tripsFile.exists()) parseAndInsertTrips(tripsFile)
            
            val stopsFile = File(extractedDir, "stops.txt")
            if (stopsFile.exists()) parseAndInsertStops(stopsFile)
            
            val stopTimesFile = File(extractedDir, "stop_times.txt")
            if (stopTimesFile.exists()) parseAndInsertStopTimes(stopTimesFile)

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error in importFromZip", e)
            return false
        } finally {
            extractedDir.deleteRecursively()
        }
    }

    private suspend fun parseAndInsertRoutes(file: File) {
        val reader = file.bufferedReader()
        val headerLine = reader.readLine() ?: return
        val header = parseCsvLine(headerLine)
        val idIdx = header.indexOf("route_id")
        val nameIdx = header.indexOf("route_short_name")
        val typeIdx = header.indexOf("route_type")

        val routes = mutableListOf<RouteEntity>()
        var line = reader.readLine()
        while (line != null) {
            val parts = parseCsvLine(line)
            if (parts.size > maxOf(idIdx, nameIdx, typeIdx)) {
                routes.add(RouteEntity(
                    routeId = parts[idIdx],
                    routeShortName = parts[nameIdx],
                    routeType = parts[typeIdx].toIntOrNull() ?: 3
                ))
            }
            line = reader.readLine()
        }
        if (routes.isNotEmpty()) dao.insertRoutes(routes)
        reader.close()
    }

    private suspend fun parseAndInsertTrips(file: File) {
        val reader = file.bufferedReader()
        val headerLine = reader.readLine() ?: return
        val header = parseCsvLine(headerLine)
        val idIdx = header.indexOf("trip_id")
        val routeIdIdx = header.indexOf("route_id")

        val batch = mutableListOf<TripEntity>()
        var line = reader.readLine()
        while (line != null) {
            val parts = parseCsvLine(line)
            if (parts.size > maxOf(idIdx, routeIdIdx)) {
                batch.add(TripEntity(
                    tripId = parts[idIdx],
                    routeId = parts[routeIdIdx]
                ))
            }
            if (batch.size >= 5000) {
                dao.insertTrips(batch)
                batch.clear()
            }
            line = reader.readLine()
        }
        if (batch.isNotEmpty()) dao.insertTrips(batch)
        reader.close()
    }

    private suspend fun parseAndInsertStops(file: File) {
        val reader = file.bufferedReader()
        val headerLine = reader.readLine() ?: return
        val header = parseCsvLine(headerLine)
        val idIdx = header.indexOf("stop_id")
        val nameIdx = header.indexOf("stop_name")

        val stops = mutableListOf<StopEntity>()
        var line = reader.readLine()
        while (line != null) {
            val parts = parseCsvLine(line)
            if (parts.size > maxOf(idIdx, nameIdx)) {
                stops.add(StopEntity(
                    stopId = parts[idIdx],
                    stopName = parts[nameIdx]
                ))
            }
            line = reader.readLine()
        }
        if (stops.isNotEmpty()) dao.insertStops(stops)
        reader.close()
    }

    private suspend fun parseAndInsertStopTimes(file: File) {
        val reader = file.bufferedReader()
        val headerLine = reader.readLine() ?: return
        val header = parseCsvLine(headerLine)
        val tripIdIdx = header.indexOf("trip_id")
        val stopIdIdx = header.indexOf("stop_id")
        val seqIdx = header.indexOf("stop_sequence")
        val arrivalIdx = header.indexOf("arrival_time")
        val departureIdx = header.indexOf("departure_time")

        val batch = mutableListOf<StopTimeEntity>()
        var line = reader.readLine()
        var count = 0
        while (line != null) {
            val parts = parseCsvLine(line)
            if (parts.size > maxOf(tripIdIdx, stopIdIdx, seqIdx, arrivalIdx, departureIdx)) {
                batch.add(StopTimeEntity(
                    tripId = parts[tripIdIdx],
                    stopId = parts[stopIdIdx],
                    stopSequence = parts[seqIdx].toIntOrNull() ?: 0,
                    arrivalTime = parts[arrivalIdx],
                    departureTime = parts[departureIdx]
                ))
            }
            if (batch.size >= 10000) {
                dao.insertStopTimes(batch)
                batch.clear()
                count += 10000
                Log.d(TAG, "Inserted $count stop times...")
            }
            line = reader.readLine()
        }
        if (batch.isNotEmpty()) dao.insertStopTimes(batch)
        reader.close()
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        for (c in line) {
            if (c == '\"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim())
                current = StringBuilder()
            } else {
                current.append(c)
            }
        }
        result.add(current.toString().trim())
        return result
    }

    suspend fun getRoutesByMode(isBus: Boolean): List<RouteEntity> = withContext(Dispatchers.IO) {
        dao.getRoutesByType(if (isBus) 3 else 0)
    }

    suspend fun getStopsForRoute(routeId: String): List<StopEntity> = withContext(Dispatchers.IO) {
        dao.getStopsForRoute(routeId)
    }
    
    fun isDataAvailable(): Boolean {
        return prefs.contains("last_modified") || prefs.contains("content_length")
    }
}
