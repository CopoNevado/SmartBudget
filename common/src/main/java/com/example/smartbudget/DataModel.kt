package com.example.smartbudget
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

data class Expense(val id: String, val cents: Long, val category: String, val timestamp: Long) {
 fun json() = JSONObject().put("id",id).put("cents",cents).put("category",category).put("timestamp",timestamp)
 fun bytes() = json().toString().toByteArray(Charsets.UTF_8)
 companion object {
  fun create(cents: Long, category: String) = Expense(UUID.randomUUID().toString(), cents, category, System.currentTimeMillis())
  fun decode(bytes: ByteArray): Expense { val j=JSONObject(bytes.toString(Charsets.UTF_8)); return Expense(j.getString("id"),j.getLong("cents"),j.getString("category"),j.getLong("timestamp")) }
 }
}
fun money(cents: Long) = String.format(Locale.US,"$%.2f",cents / 100.0)
fun parseCents(s: String): Long? = try { s.toBigDecimal().movePointRight(2).longValueExact().takeIf { it in 1..100000000L } } catch(e: Exception) { null }
