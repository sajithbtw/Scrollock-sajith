package com.sajith.scrolllock.workout

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes and deserializes recorded repetition landmark sequences locally using org.json
 * so users can replay and inspect joint angles from any completed or rejected rep.
 */
object RepReplaySerializer {

    fun toJson(records: List<RepAnalysisRecord>): String {
        val arr = JSONArray()
        for (rep in records) {
            val obj = JSONObject()
            obj.put("repNumber", rep.repNumber)
            obj.put("exercise", rep.exercise.name)
            obj.put("isValid", rep.isValid)
            obj.put("rejectionReason", rep.rejectionReason ?: "")
            obj.put("durationMs", rep.durationMs)
            obj.put("minAngle", rep.minPrimaryAngle.toDouble())
            obj.put("maxAngle", rep.maxPrimaryAngle.toDouble())
            obj.put("asymmetry", rep.leftRightAsymmetryDeg.toDouble())
            obj.put("formScore", rep.formQualityScore)

            val cuesArr = JSONArray()
            rep.postureCues.forEach { cuesArr.put(it) }
            obj.put("cues", cuesArr)

            val framesArr = JSONArray()
            // Downsample slightly if needed to keep local prefs compact
            val step = if (rep.frames.size > 36) 2 else 1
            for (i in rep.frames.indices step step) {
                val frame = rep.frames[i]
                val fObj = JSONObject()
                fObj.put("ts", frame.timestampMs)
                fObj.put("fb", frame.feedback ?: "")

                val lmsObj = JSONObject()
                for ((id, pt) in frame.landmarks) {
                    val pObj = JSONObject()
                    pObj.put("x", pt.x.toDouble())
                    pObj.put("y", pt.y.toDouble())
                    pObj.put("c", pt.inFrameLikelihood.toDouble())
                    lmsObj.put(id.toString(), pObj)
                }
                fObj.put("lms", lmsObj)

                val errArr = JSONArray()
                frame.errorJoints.forEach { errArr.put(it) }
                fObj.put("errs", errArr)

                val angObj = JSONObject()
                frame.jointAngles.forEach { (k, v) -> angObj.put(k, v.toDouble()) }
                fObj.put("ang", angObj)

                framesArr.put(fObj)
            }
            obj.put("frames", framesArr)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun fromJson(json: String): List<RepAnalysisRecord> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<RepAnalysisRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val exercise = try {
                    ExerciseType.valueOf(obj.optString("exercise", "SQUATS"))
                } catch (_: Exception) {
                    ExerciseType.SQUATS
                }
                val cues = mutableListOf<String>()
                val cuesArr = obj.optJSONArray("cues")
                if (cuesArr != null) {
                    for (j in 0 until cuesArr.length()) cues.add(cuesArr.getString(j))
                }
                val frames = mutableListOf<PoseFrame>()
                val framesArr = obj.optJSONArray("frames")
                if (framesArr != null) {
                    for (j in 0 until framesArr.length()) {
                        val fObj = framesArr.getJSONObject(j)
                        val lmsMap = mutableMapOf<Int, LandmarkPoint>()
                        val lmsObj = fObj.optJSONObject("lms")
                        if (lmsObj != null) {
                            val keys = lmsObj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                val id = k.toIntOrNull() ?: continue
                                val pObj = lmsObj.getJSONObject(k)
                                lmsMap[id] = LandmarkPoint(
                                    type = id,
                                    x = pObj.optDouble("x", 0.5).toFloat(),
                                    y = pObj.optDouble("y", 0.5).toFloat(),
                                    inFrameLikelihood = pObj.optDouble("c", 1.0).toFloat()
                                )
                            }
                        }
                        val errSet = mutableSetOf<Int>()
                        val errArr = fObj.optJSONArray("errs")
                        if (errArr != null) {
                            for (eIdx in 0 until errArr.length()) errSet.add(errArr.getInt(eIdx))
                        }
                        val angMap = mutableMapOf<String, Float>()
                        val angObj = fObj.optJSONObject("ang")
                        if (angObj != null) {
                            val aKeys = angObj.keys()
                            while (aKeys.hasNext()) {
                                val ak = aKeys.next()
                                angMap[ak] = angObj.optDouble(ak, 0.0).toFloat()
                            }
                        }
                        val fb = fObj.optString("fb", "").ifBlank { null }
                        frames.add(PoseFrame(fObj.optLong("ts", 0L), lmsMap, angMap, errSet, fb))
                    }
                }
                val rej = obj.optString("rejectionReason", "").ifBlank { null }
                list.add(
                    RepAnalysisRecord(
                        repNumber = obj.optInt("repNumber", i + 1),
                        exercise = exercise,
                        isValid = obj.optBoolean("isValid", true),
                        rejectionReason = rej,
                        durationMs = obj.optLong("durationMs", 1200L),
                        minPrimaryAngle = obj.optDouble("minAngle", 95.0).toFloat(),
                        maxPrimaryAngle = obj.optDouble("maxAngle", 170.0).toFloat(),
                        leftRightAsymmetryDeg = obj.optDouble("asymmetry", 0.0).toFloat(),
                        formQualityScore = obj.optInt("formScore", 100),
                        postureCues = cues,
                        frames = frames
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
