package com.dacia.dokkeranalyse.data

import android.content.Context
import com.dacia.dokkeranalyse.model.ObdPid
import com.dacia.dokkeranalyse.model.PidCategory
import org.json.JSONArray
import org.json.JSONObject

class PidRepository(context: Context) {

    private val prefs = context.getSharedPreferences("dokker_pids_prefs", Context.MODE_PRIVATE)

    fun loadPids(): List<ObdPid> {
        val rawJson = prefs.getString(KEY_PID_LIST, null)
        if (rawJson.isNullOrBlank()) {
            val defaults = DefaultPids.getDaciaDokker2014Defaults()
            savePids(defaults)
            return defaults
        }

        return try {
            val arr = JSONArray(rawJson)
            val list = mutableListOf<ObdPid>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val catName = obj.optString("category", PidCategory.CUSTOM.name)
                val category = runCatching { PidCategory.valueOf(catName) }
                    .getOrDefault(PidCategory.CUSTOM)

                list.add(
                    ObdPid(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        shortName = obj.optString("shortName", obj.getString("name").take(10)),
                        description = obj.optString("description", ""),
                        category = category,
                        txHeader = obj.optString("txHeader", "7E0"),
                        rxHeader = obj.optString("rxHeader", "7E8"),
                        initCommand = obj.optString("initCommand", ""),
                        modeAndPid = obj.optString("modeAndPid", "0105"),
                        formula = obj.optString("formula", "A-40"),
                        unit = obj.optString("unit", "°C"),
                        minValue = obj.optDouble("minValue", -40.0).toFloat(),
                        maxValue = obj.optDouble("maxValue", 100.0).toFloat(),
                        decimals = obj.optInt("decimals", 1),
                        enabled = obj.optBoolean("enabled", true),
                        isPrimaryEvaporator = obj.optBoolean("isPrimaryEvaporator", false)
                    )
                )
            }
            if (list.isEmpty()) DefaultPids.getDaciaDokker2014Defaults() else list
        } catch (e: Exception) {
            DefaultPids.getDaciaDokker2014Defaults()
        }
    }

    fun savePids(pids: List<ObdPid>) {
        val arr = JSONArray()
        for (pid in pids) {
            val obj = JSONObject().apply {
                put("id", pid.id)
                put("name", pid.name)
                put("shortName", pid.shortName)
                put("description", pid.description)
                put("category", pid.category.name)
                put("txHeader", pid.txHeader)
                put("rxHeader", pid.rxHeader)
                put("initCommand", pid.initCommand)
                put("modeAndPid", pid.modeAndPid)
                put("formula", pid.formula)
                put("unit", pid.unit)
                put("minValue", pid.minValue.toDouble())
                put("maxValue", pid.maxValue.toDouble())
                put("decimals", pid.decimals)
                put("enabled", pid.enabled)
                put("isPrimaryEvaporator", pid.isPrimaryEvaporator)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_PID_LIST, arr.toString()).apply()
    }

    fun resetToDefaults(): List<ObdPid> {
        val defaults = DefaultPids.getDaciaDokker2014Defaults()
        savePids(defaults)
        return defaults
    }

    fun getLastDeviceAddress(): String? = prefs.getString(KEY_LAST_BT_ADDRESS, null)

    fun saveLastDeviceAddress(address: String) {
        prefs.edit().putString(KEY_LAST_BT_ADDRESS, address).apply()
    }

    companion object {
        private const val KEY_PID_LIST = "pid_list_v3_8212_abo_verified"
        private const val KEY_LAST_BT_ADDRESS = "last_bt_address"
    }
}
