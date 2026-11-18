package com.bettermifitness.sync.health

import com.bettermifitness.sync.i18n.L10n
import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import com.bettermifitness.sync.data.time.MiTimezone
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.StepsRecord as HcStepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Pressure
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.units.TemperatureDelta
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.bettermifitness.sync.data.api.ActiveCaloriesSample
import com.bettermifitness.sync.data.api.BloodPressureSample
import com.bettermifitness.sync.data.api.DistanceSample
import com.bettermifitness.sync.data.api.HeartRateSample
import com.bettermifitness.sync.data.api.HrvSample
import com.bettermifitness.sync.data.api.SleepSession
import com.bettermifitness.sync.data.api.SpO2Sample
import com.bettermifitness.sync.data.api.StepsRecord
import com.bettermifitness.sync.data.api.TemperatureSample
import com.bettermifitness.sync.data.api.Vo2MaxSample
import com.bettermifitness.sync.data.api.WeightMeasurement
import com.bettermifitness.sync.data.api.WorkoutSession
import java.time.Instant
import kotlin.math.roundToLong

@Suppress("LargeClass")
actual class HealthWriter(private val context: Context) : HealthStore {
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    /**
     * Write permissions for types this HC install supports.
     * Older phones (e.g. Galaxy S8 + old Health Connect) may not expose skin temperature.
     */
    private fun availableWritePermissions(): Set<String> {
        val perms = linkedSetOf(
            HealthPermission.getWritePermission(HeartRateRecord::class),
            HealthPermission.getWritePermission(RestingHeartRateRecord::class),
            HealthPermission.getWritePermission(SleepSessionRecord::class),
            HealthPermission.getWritePermission(HcStepsRecord::class),
            HealthPermission.getWritePermission(DistanceRecord::class),
            HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getWritePermission(OxygenSaturationRecord::class),
            HealthPermission.getWritePermission(WeightRecord::class),
            HealthPermission.getWritePermission(BodyFatRecord::class),
            HealthPermission.getWritePermission(BoneMassRecord::class),
            HealthPermission.getWritePermission(BodyWaterMassRecord::class),
            HealthPermission.getWritePermission(LeanBodyMassRecord::class),
            HealthPermission.getWritePermission(BasalMetabolicRateRecord::class),
            HealthPermission.getWritePermission(HydrationRecord::class),
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE,
            HealthPermission.getWritePermission(ElevationGainedRecord::class),
            HealthPermission.getWritePermission(SpeedRecord::class),
            HealthPermission.getWritePermission(StepsCadenceRecord::class),
            HealthPermission.getWritePermission(CyclingPedalingCadenceRecord::class),
            HealthPermission.getWritePermission(PowerRecord::class),
            HealthPermission.getWritePermission(FloorsClimbedRecord::class),
            HealthPermission.getWritePermission(BloodPressureRecord::class),
            HealthPermission.getWritePermission(BodyTemperatureRecord::class),
            HealthPermission.getWritePermission(Vo2MaxRecord::class),
            HealthPermission.getWritePermission(HeartRateVariabilityRmssdRecord::class),
        )
        val skinOk = try {
            client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        } catch (_: Exception) {
            false
        }
        if (skinOk) {
            perms += HealthPermission.getWritePermission(SkinTemperatureRecord::class)
        }
        return perms
    }

    private fun availableReadPermissions(): Set<String> {
        val perms = linkedSetOf(
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(BodyFatRecord::class),
            HealthPermission.getReadPermission(BoneMassRecord::class),
            HealthPermission.getReadPermission(BodyWaterMassRecord::class),
            HealthPermission.getReadPermission(LeanBodyMassRecord::class),
            HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
            HealthPermission.getReadPermission(HydrationRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HcStepsRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(ElevationGainedRecord::class),
            HealthPermission.getReadPermission(SpeedRecord::class),
            HealthPermission.getReadPermission(StepsCadenceRecord::class),
            HealthPermission.getReadPermission(CyclingPedalingCadenceRecord::class),
            HealthPermission.getReadPermission(PowerRecord::class),
            HealthPermission.getReadPermission(FloorsClimbedRecord::class),
            HealthPermission.getReadPermission(BloodPressureRecord::class),
            HealthPermission.getReadPermission(BodyTemperatureRecord::class),
            HealthPermission.getReadPermission(Vo2MaxRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        )
        val skinOk = try {
            client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        } catch (_: Exception) {
            false
        }
        if (skinOk) {
            perms += HealthPermission.getReadPermission(SkinTemperatureRecord::class)
        }
        return perms
    }

    private fun availablePermissions(): Set<String> =
        availableWritePermissions() + availableReadPermissions()

    private fun zoneOffsetToMiUnits(zoneOffset: java.time.ZoneOffset?): Int? {
        if (zoneOffset == null) return null
        val seconds = zoneOffset.totalSeconds
        val inRange = seconds in
            MiTimezone.MIN_OFFSET_SECONDS..MiTimezone.MAX_OFFSET_SECONDS
        if (!inRange) return null
        // 15-minute units
        return seconds / (15 * 60)
    }

    private fun isSelfWritten(clientRecordId: String?, packageName: String?): Boolean {
        if (clientRecordId?.startsWith("mifit-") == true) return true
        if (packageName != null && packageName == context.packageName) return true
        return false
    }

    actual override suspend fun writeHeartRate(samples: List<HeartRateSample>) {
        val sortedSamples = HealthDataNormalizer.normalizeHeartRate(samples)
        if (sortedSamples.isEmpty()) return

        // Group into fixed windows so clientRecordId stays stable across re-syncs.
        val groups = sortedSamples.groupBy { HealthRecordIds.heartRateWindowStart(it.timestamp) }

        val records = groups.mapNotNull { (windowStart, group) ->
            val ordered = group.sortedBy { it.timestamp }
            val start = Instant.ofEpochSecond(ordered.first().timestamp)
            val end = Instant.ofEpochSecond(ordered.last().timestamp).plusSeconds(1)
            if (!end.isAfter(start)) return@mapNotNull null
            val zo = ZoneOffsetResolver.fromMiOrSystem(
                ordered.firstNotNullOfOrNull { it.tzIn15Min },
                start,
            )

            HeartRateRecord(
                startTime = start,
                endTime = end,
                startZoneOffset = zo,
                endZoneOffset = zo,
                samples = ordered.map { sample ->
                    HeartRateRecord.Sample(
                        time = Instant.ofEpochSecond(sample.timestamp),
                        beatsPerMinute = sample.bpm.toLong(),
                    )
                },
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.heartRateWindow(windowStart),
                    clientRecordVersion = HealthRecordIds.version(
                        ordered.joinToString("|") { "${it.timestamp}:${it.bpm}:${it.tzIn15Min}" },
                    ),
                ),
            )
        }

        insertChunked(records)
    }

    actual override suspend fun writeRestingHeartRate(samples: List<HeartRateSample>) {
        val clean = HealthDataNormalizer.normalizeHeartRate(samples)
        if (clean.isEmpty()) return
        val records = clean.map { sample ->
            val time = Instant.ofEpochSecond(sample.timestamp)
            RestingHeartRateRecord(
                time = time,
                zoneOffset = ZoneOffsetResolver.fromMiOrSystem(sample.tzIn15Min, time),
                beatsPerMinute = sample.bpm.toLong(),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.restingHeartRate(sample.timestamp),
                    clientRecordVersion = HealthRecordIds.version(
                        sample.timestamp,
                        sample.bpm,
                        sample.tzIn15Min,
                    ),
                ),
            )
        }
        insertChunked(records)
    }

    actual override suspend fun writeSleep(sessions: List<SleepSession>) {
        val clean = HealthDataNormalizer.normalizeSleep(sessions)
        if (clean.isEmpty()) return
        val records = clean.mapNotNull { session ->
            val start = Instant.ofEpochSecond(session.startTime)
            val end = Instant.ofEpochSecond(session.endTime)
            if (!end.isAfter(start)) return@mapNotNull null
            val zo = ZoneOffsetResolver.fromMiOrSystem(session.tzIn15Min, start)

            SleepSessionRecord(
                startTime = start,
                endTime = end,
                startZoneOffset = zo,
                endZoneOffset = zo,
                stages = session.stages.mapNotNull { stage ->
                    val s = Instant.ofEpochSecond(stage.startTime)
                    val e = Instant.ofEpochSecond(stage.endTime)
                    if (!e.isAfter(s)) return@mapNotNull null
                    SleepSessionRecord.Stage(
                        startTime = s,
                        endTime = e,
                        stage = mapSleepStage(stage.stage),
                    )
                },
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.sleepSession(session.startTime),
                    // Monotonic in end time so fuller/later syncs overwrite stale
                    // truncated sessions (HC upsert needs a higher version).
                    clientRecordVersion = HealthRecordIds.counterVersion(
                        session.endTime,
                        session.stages.joinToString { "${it.startTime}:${it.stage}" },
                        session.tzIn15Min,
                    ),
                ),
            )
        }
        insertChunked(records)
    }

    actual override suspend fun writeSteps(records: List<StepsRecord>) {
        val clean = HealthDataNormalizer.normalizeSteps(records)
        if (clean.isEmpty()) return
        val hcRecords = clean.mapNotNull { record ->
            val ts = record.date.toLong()
            val start = Instant.ofEpochSecond(ts)
            // Clamp open hour end so HC never sees endTime in the future (issue #10 class).
            val end = minOf(start.plusSeconds(3599), Instant.now())
            if (!end.isAfter(start)) return@mapNotNull null
            val zo = ZoneOffsetResolver.fromMiOrSystem(record.tzIn15Min, start)

            HcStepsRecord(
                startTime = start,
                endTime = end,
                startZoneOffset = zo,
                endZoneOffset = zo,
                count = record.steps.toLong(),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.steps(ts),
                    // Higher step counts during the day should replace earlier partial buckets.
                    // Workout-adjusted buckets carry a bump so the (lower) corrected value
                    // still overwrites previously synced inflated history (issue #51).
                    clientRecordVersion = HealthRecordIds.counterVersion(record.steps.toLong(), ts) +
                        if (record.workoutAdjusted) HealthRecordIds.WORKOUT_ADJUSTMENT_VERSION_BUMP else 0L,
                ),
            )
        }
        insertChunked(hcRecords)
    }

    actual override suspend fun writeDistance(samples: List<DistanceSample>) {
        val clean = HealthDataNormalizer.normalizeDistance(samples)
        if (clean.isEmpty()) return
        val records = clean.map { s ->
            val start = Instant.ofEpochSecond(s.startTime)
            val zo = ZoneOffsetResolver.fromMiOrSystem(s.tzIn15Min, start)
            DistanceRecord(
                startTime = start,
                endTime = Instant.ofEpochSecond(s.endTime),
                startZoneOffset = zo,
                endZoneOffset = zo,
                distance = Length.meters(s.meters),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.distance(s.startTime),
                    // Counter-based so mid-day growth still wins; adjusted buckets carry a
                    // bump so the correction overwrites inflated history (issue #51).
                    clientRecordVersion = HealthRecordIds.counterVersion(
                        (s.meters * 1000).roundToLong(),
                        s.startTime,
                    ) + if (s.workoutAdjusted) HealthRecordIds.WORKOUT_ADJUSTMENT_VERSION_BUMP else 0L,
                ),
            )
        }
        insertChunked(records)
    }

    actual override suspend fun writeActiveCalories(samples: List<ActiveCaloriesSample>) {
        val clean = HealthDataNormalizer.normalizeActiveCalories(samples)
        if (clean.isEmpty()) return
        val records = clean.map { s ->
            val start = Instant.ofEpochSecond(s.startTime)
            val zo = ZoneOffsetResolver.fromMiOrSystem(s.tzIn15Min, start)
            ActiveCaloriesBurnedRecord(
                startTime = start,
                endTime = Instant.ofEpochSecond(s.endTime),
                startZoneOffset = zo,
                endZoneOffset = zo,
                energy = Energy.kilocalories(s.kilocalories),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.activeCalories(s.startTime),
                    // Counter-based so mid-day growth still wins; adjusted buckets carry a
                    // bump so the correction overwrites inflated history (issue #51).
                    clientRecordVersion = HealthRecordIds.counterVersion(
                        (s.kilocalories * 1000).roundToLong(),
                        s.startTime,
                    ) + if (s.workoutAdjusted) HealthRecordIds.WORKOUT_ADJUSTMENT_VERSION_BUMP else 0L,
                ),
            )
        }
        insertChunked(records)
    }

    actual override suspend fun writeWeight(measurements: List<WeightMeasurement>) {
        val clean = HealthDataNormalizer.normalizeWeight(measurements)
        if (clean.isEmpty()) return
        val weightRecords = clean.map { m ->
            val time = Instant.ofEpochSecond(m.timestamp)
            WeightRecord(
                time = time,
                zoneOffset = ZoneOffsetResolver.fromMiOrSystem(m.tzIn15Min, time),
                weight = Mass.kilograms(m.weightKg),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.weight(m.timestamp),
                    clientRecordVersion = HealthRecordIds.version(
                        m.timestamp,
                        m.weightKg,
                        m.tzIn15Min,
                    ),
                ),
            )
        }
        insertChunked(weightRecords)
        val fatRecords = clean.mapNotNull { m ->
            val fat = m.bodyFatPercent ?: return@mapNotNull null
            val time = Instant.ofEpochSecond(m.timestamp)
            BodyFatRecord(
                time = time,
                zoneOffset = ZoneOffsetResolver.fromMiOrSystem(m.tzIn15Min, time),
                percentage = Percentage(fat),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.bodyFat(m.timestamp),
                    clientRecordVersion = HealthRecordIds.version(m.timestamp, fat, m.tzIn15Min),
                ),
            )
        }
        try {
            insertChunked(fatRecords)
        } catch (_: Exception) { }

        val boneRecords = clean.mapNotNull { m ->
            val bone = m.boneMassKg ?: return@mapNotNull null
            val time = Instant.ofEpochSecond(m.timestamp)
            BoneMassRecord(
                time = time,
                zoneOffset = ZoneOffsetResolver.fromMiOrSystem(m.tzIn15Min, time),
                mass = Mass.kilograms(bone),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.boneMass(m.timestamp),
                    clientRecordVersion = HealthRecordIds.version(m.timestamp, bone, m.tzIn15Min),
                ),
            )
        }
        try {
            insertChunked(boneRecords)
        } catch (_: Exception) { }

        val muscleRecords = clean.mapNotNull { m ->
            val muscle = m.muscleMassKg ?: return@mapNotNull null
            val time = Instant.ofEpochSecond(m.timestamp)
            LeanBodyMassRecord(
                time = time,
                zoneOffset = ZoneOffsetResolver.fromMiOrSystem(m.tzIn15Min, time),
                mass = Mass.kilograms(muscle),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.leanBodyMass(m.timestamp),
                    clientRecordVersion = HealthRecordIds.version(m.timestamp, muscle, m.tzIn15Min),
                ),
            )
        }
        try {
            insertChunked(muscleRecords)
        } catch (_: Exception) { }

        val bmrRecords = clean.mapNotNull { m ->
            val bmr = m.basalMetabolismKcal ?: return@mapNotNull null
            val time = Instant.ofEpochSecond(m.timestamp)
            BasalMetabolicRateRecord(
                time = time,
                zoneOffset = ZoneOffsetResolver.fromMiOrSystem(m.tzIn15Min, time),
                basalMetabolicRate = androidx.health.connect.client.units.Power.kilocaloriesPerDay(bmr),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.basalMetabolism(m.timestamp),
                    clientRecordVersion = HealthRecordIds.version(m.timestamp, bmr, m.tzIn15Min),
                ),
            )
        }
        try {
            insertChunked(bmrRecords)
        } catch (_: Exception) { }

        val waterRecords = clean.mapNotNull { m ->
            val waterPercent = m.waterPercent ?: return@mapNotNull null
            val waterKg = (waterPercent / 100.0) * m.weightKg
            val time = Instant.ofEpochSecond(m.timestamp)
            BodyWaterMassRecord(
                time = time,
                zoneOffset = ZoneOffsetResolver.fromMiOrSystem(m.tzIn15Min, time),
                mass = Mass.kilograms(waterKg),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.bodyWater(m.timestamp),
                    clientRecordVersion = HealthRecordIds.version(m.timestamp, waterKg, m.tzIn15Min),
                ),
            )
        }
        try {
            insertChunked(waterRecords)
        } catch (_: Exception) { }
    }

    actual override suspend fun writeWorkouts(sessions: List<WorkoutSession>) {
        val clean = HealthDataNormalizer.normalizeWorkouts(sessions)
        if (clean.isEmpty()) return
        val factory = AndroidWorkoutRecordFactory()
        val batch = clean.flatMap { factory.buildRecords(it) }
        // Insert in chunks to avoid binder limits on large routes/HR
        batch.chunked(50).forEach { chunk ->
            client.insertRecords(chunk)
        }
    }

    actual override suspend fun writeSpO2(samples: List<SpO2Sample>) {
        val clean = HealthDataNormalizer.normalizeSpO2(samples)
        if (clean.isEmpty()) return
        val records = clean.map { sample ->
            val time = Instant.ofEpochSecond(sample.timestamp)
            OxygenSaturationRecord(
                time = time,
                zoneOffset = ZoneOffsetResolver.fromMiOrSystem(sample.tzIn15Min, time),
                percentage = Percentage(sample.percentage.toDouble()),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.spo2(sample.timestamp),
                    clientRecordVersion = HealthRecordIds.version(
                        sample.timestamp,
                        sample.percentage,
                        sample.tzIn15Min,
                    ),
                ),
            )
        }
        insertChunked(records)
    }

    actual override suspend fun writeBloodPressure(samples: List<BloodPressureSample>) {
        val clean = HealthDataNormalizer.normalizeBloodPressure(samples)
        if (clean.isEmpty()) return
        val records = clean.map { s ->
            val time = Instant.ofEpochSecond(s.timestamp)
            BloodPressureRecord(
                time = time,
                zoneOffset = ZoneOffsetResolver.fromMiOrSystem(s.tzIn15Min, time),
                systolic = Pressure.millimetersOfMercury(s.systolicMmhg.toDouble()),
                diastolic = Pressure.millimetersOfMercury(s.diastolicMmhg.toDouble()),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.bloodPressure(s.timestamp),
                    clientRecordVersion = HealthRecordIds.version(
                        s.timestamp,
                        s.systolicMmhg,
                        s.diastolicMmhg,
                        s.pulseBpm,
                        s.tzIn15Min,
                    ),
                ),
            )
        }
        insertChunked(records)
    }

    actual override suspend fun writeTemperature(samples: List<TemperatureSample>) {
        val clean = HealthDataNormalizer.normalizeTemperature(samples)
        if (clean.isEmpty()) return
        val bodyRecords = clean.mapNotNull { s ->
            val body = s.bodyCelsius ?: return@mapNotNull null
            val time = Instant.ofEpochSecond(s.timestamp)
            BodyTemperatureRecord(
                time = time,
                zoneOffset = ZoneOffsetResolver.fromMiOrSystem(s.tzIn15Min, time),
                temperature = Temperature.celsius(body),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.bodyTemperature(s.timestamp),
                    clientRecordVersion = HealthRecordIds.version(s.timestamp, body, s.tzIn15Min),
                ),
            )
        }
        insertChunked(bodyRecords)

        // SkinTemperatureRecord needs a baseline + deltas; write as single-point baseline.
        val skinRecords = clean.mapNotNull { s ->
            val skin = s.skinCelsius ?: return@mapNotNull null
            val start = Instant.ofEpochSecond(s.timestamp)
            val end = start.plusSeconds(1)
            val zo = ZoneOffsetResolver.fromMiOrSystem(s.tzIn15Min, start)
            SkinTemperatureRecord(
                startTime = start,
                endTime = end,
                startZoneOffset = zo,
                endZoneOffset = zo,
                deltas = listOf(
                    SkinTemperatureRecord.Delta(
                        time = start,
                        delta = TemperatureDelta.celsius(0.0),
                    ),
                ),
                baseline = Temperature.celsius(skin),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.skinTemperature(s.timestamp),
                    clientRecordVersion = HealthRecordIds.version(s.timestamp, skin, s.tzIn15Min),
                ),
            )
        }
        insertChunked(skinRecords)
    }

    actual override suspend fun writeVo2Max(samples: List<Vo2MaxSample>) {
        val clean = HealthDataNormalizer.normalizeVo2Max(samples)
        if (clean.isEmpty()) return
        val records = clean.map { s ->
            val time = Instant.ofEpochSecond(s.timestamp)
            Vo2MaxRecord(
                time = time,
                zoneOffset = ZoneOffsetResolver.fromMiOrSystem(s.tzIn15Min, time),
                vo2MillilitersPerMinuteKilogram = s.mlPerKgMin,
                measurementMethod = Vo2MaxRecord.MEASUREMENT_METHOD_OTHER,
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.vo2Max(s.timestamp),
                    clientRecordVersion = HealthRecordIds.version(
                        s.timestamp,
                        s.mlPerKgMin,
                        s.tzIn15Min,
                    ),
                ),
            )
        }
        insertChunked(records)
    }

    actual override suspend fun writeHrv(samples: List<HrvSample>) {
        val clean = HealthDataNormalizer.normalizeHrv(samples)
        if (clean.isEmpty()) return
        // Mi overnight HRV is in ms; Health Connect stores RMSSD milliseconds.
        val records = clean.map { s ->
            val time = Instant.ofEpochSecond(s.timestamp)
            HeartRateVariabilityRmssdRecord(
                time = time,
                zoneOffset = ZoneOffsetResolver.fromMiOrSystem(s.tzIn15Min, time),
                heartRateVariabilityMillis = s.hrvMs,
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.hrv(s.timestamp),
                    clientRecordVersion = HealthRecordIds.version(
                        s.timestamp,
                        s.hrvMs,
                        s.tzIn15Min,
                    ),
                ),
            )
        }
        insertChunked(records)
    }

    actual override suspend fun readLatestWeight(): WeightMeasurement? {
        if (!isAvailable()) return null
        return try {
            val windowStart = Instant.now().minus(java.time.Duration.ofDays(28))
            var latest: WeightRecord? = null
            var pageToken: String? = null
            do {
                val response = client.readRecords(
                    ReadRecordsRequest(
                        WeightRecord::class,
                        timeRangeFilter = TimeRangeFilter.after(windowStart),
                        pageToken = pageToken,
                    ),
                )
                for (r in response.records) {
                    if (isSelfWritten(r.metadata.clientRecordId, r.metadata.dataOrigin.packageName)) continue
                    if (latest == null || r.time.isAfter(latest.time)) latest = r
                }
                pageToken = response.pageToken
            } while (pageToken != null)
            val r = latest ?: return null
            val ts = r.time.epochSecond
            // HealthDataNormalizer will filter implausible, but double-check
            val kg = r.weight.inKilograms
            if (kg !in 1.0..500.0) return null

            val fatPercent = try {
                val fatResponse = client.readRecords(
                    ReadRecordsRequest(
                        BodyFatRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(
                            r.time.minusSeconds(300),
                            r.time.plusSeconds(300),
                        ),
                    ),
                )
                fatResponse.records
                    .filter { !isSelfWritten(it.metadata.clientRecordId, it.metadata.dataOrigin.packageName) }
                    .minByOrNull { kotlin.math.abs(it.time.epochSecond - r.time.epochSecond) }
                    ?.percentage?.value
                    ?.let { kotlin.math.round(it * 10.0) / 10.0 }
            } catch (_: Exception) {
                null
            }

            val boneMass = try {
                val boneResponse = client.readRecords(
                    ReadRecordsRequest(
                        BoneMassRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(
                            r.time.minusSeconds(300),
                            r.time.plusSeconds(300),
                        ),
                    ),
                )
                boneResponse.records
                    .filter { !isSelfWritten(it.metadata.clientRecordId, it.metadata.dataOrigin.packageName) }
                    .minByOrNull { kotlin.math.abs(it.time.epochSecond - r.time.epochSecond) }
                    ?.mass?.inKilograms
                    ?.let { kotlin.math.round(it * 100.0) / 100.0 }
            } catch (_: Exception) {
                null
            }

            val muscleMass = try {
                val muscleResponse = client.readRecords(
                    ReadRecordsRequest(
                        LeanBodyMassRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(
                            r.time.minusSeconds(300),
                            r.time.plusSeconds(300),
                        ),
                    ),
                )
                muscleResponse.records
                    .filter { !isSelfWritten(it.metadata.clientRecordId, it.metadata.dataOrigin.packageName) }
                    .minByOrNull { kotlin.math.abs(it.time.epochSecond - r.time.epochSecond) }
                    ?.mass?.inKilograms
                    ?.let { kotlin.math.round(it * 10.0) / 10.0 }
            } catch (_: Exception) {
                null
            }

            val bmr = try {
                val bmrResponse = client.readRecords(
                    ReadRecordsRequest(
                        BasalMetabolicRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(
                            r.time.minusSeconds(300),
                            r.time.plusSeconds(300),
                        ),
                    ),
                )
                bmrResponse.records
                    .filter { !isSelfWritten(it.metadata.clientRecordId, it.metadata.dataOrigin.packageName) }
                    .minByOrNull { kotlin.math.abs(it.time.epochSecond - r.time.epochSecond) }
                    ?.basalMetabolicRate?.inKilocaloriesPerDay
                    ?.let { kotlin.math.round(it) }
            } catch (_: Exception) {
                null
            }

            val waterPercent = try {
                val waterResponse = client.readRecords(
                    ReadRecordsRequest(
                        BodyWaterMassRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(
                            r.time.minusSeconds(300),
                            r.time.plusSeconds(300),
                        ),
                    ),
                )
                val waterKg = waterResponse.records
                    .filter { !isSelfWritten(it.metadata.clientRecordId, it.metadata.dataOrigin.packageName) }
                    .minByOrNull { kotlin.math.abs(it.time.epochSecond - r.time.epochSecond) }
                    ?.mass?.inKilograms
                if (waterKg != null && kg > 0) {
                    kotlin.math.round((waterKg / kg) * 1000.0) / 10.0
                } else null
            } catch (_: Exception) {
                null
            }

            WeightMeasurement(
                timestamp = ts,
                weightKg = kg,
                bodyFatPercent = fatPercent,
                muscleMassKg = muscleMass,
                boneMassKg = boneMass,
                basalMetabolismKcal = bmr,
                waterPercent = waterPercent,
                tzIn15Min = zoneOffsetToMiUnits(r.zoneOffset),
            ).let { listOf(it) }.let { HealthDataNormalizer.normalizeWeight(it).firstOrNull() }
        } catch (_: Exception) {
            null
        }
    }

    actual override suspend fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    actual override suspend fun hasWritePermissions(): Boolean {
        if (!isAvailable()) return false
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            if (granted.isEmpty()) return false
            val core = setOf(
                HealthPermission.getWritePermission(WeightRecord::class),
                HealthPermission.getWritePermission(HcStepsRecord::class),
            )
            core.any { it in granted }
        } catch (_: Exception) {
            false
        }
    }

    actual override suspend fun requestPermissions() {
        val launcher = HealthConnectPermissionBridge.requestPermissions
            ?: throw IllegalStateException(L10n.text(L10n.healthPermissionsIncomplete))
        val needed = availablePermissions()
        if (needed.isEmpty()) {
            throw Exception(L10n.text(L10n.healthNotAvailable))
        }
        val granted = launcher(needed)
        if (granted.isEmpty()) {
            throw Exception(L10n.text(L10n.healthPermissionsIncomplete))
        }
    }

    actual override fun healthServiceName(): String = "Health Connect"

    actual override suspend fun availabilityHint(): String? {
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE ->
                if (hasWritePermissions()) {
                    null
                } else {
                    L10n.textFmt(L10n.homeAllowAccessDetail, healthServiceName())
                }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                L10n.text(L10n.healthNotAvailable)
            else ->
                L10n.text(L10n.healthNotAvailable)
        }
    }

    actual override fun openHealthService() {
        HealthConnectPermissionBridge.openHealthConnect?.invoke()
    }

    /** Uses the shared raw Mi state mapping so Android and iOS stay consistent. */
    private fun mapSleepStage(stage: Int): Int =
        when (HealthDataNormalizer.miSleepStageKind(stage)) {
            HealthDataNormalizer.MiSleepStageKind.AWAKE -> SleepSessionRecord.STAGE_TYPE_AWAKE
            HealthDataNormalizer.MiSleepStageKind.DEEP -> SleepSessionRecord.STAGE_TYPE_DEEP
            HealthDataNormalizer.MiSleepStageKind.LIGHT -> SleepSessionRecord.STAGE_TYPE_LIGHT
            HealthDataNormalizer.MiSleepStageKind.REM -> SleepSessionRecord.STAGE_TYPE_REM
            HealthDataNormalizer.MiSleepStageKind.UNKNOWN -> SleepSessionRecord.STAGE_TYPE_UNKNOWN
        }

    /**
     * Chunked insert to avoid binder/transaction limits on large batches.
     * Record IDs and versions are unchanged — chunking only splits transport.
     */
    private suspend fun insertChunked(
        records: List<androidx.health.connect.client.records.Record>,
        chunkSize: Int = HEALTH_WRITE_CHUNK_SIZE,
    ) {
        if (records.isEmpty()) return
        records.chunked(chunkSize).forEach { chunk ->
            client.insertRecords(chunk)
        }
    }

    companion object {
        /** Transport batch size for Health Connect inserts (workouts use 50 for route-heavy batches). */
        const val HEALTH_WRITE_CHUNK_SIZE = 300
    }
}
